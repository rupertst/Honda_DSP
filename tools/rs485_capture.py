#!/usr/bin/env python3
from __future__ import annotations

import argparse
import glob
import string
import sys
import time
from datetime import datetime
from pathlib import Path
from typing import Callable, Iterable

DEFAULT_PORT_PATTERNS = (
    "/dev/cu.usbserial*",
    "/dev/cu.usbmodem*",
    "/dev/tty.usbserial*",
    "/dev/tty.usbmodem*",
)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Capture RS485 traffic from a USB serial adapter."
    )
    parser.add_argument("--port", help="Serial port path. Defaults to auto-detect.")
    parser.add_argument("--baudrate", type=int, default=9600, help="Baud rate.")
    parser.add_argument(
        "--timeout",
        type=float,
        default=0.2,
        help="Serial read timeout in seconds.",
    )
    parser.add_argument(
        "--read-size",
        type=int,
        default=256,
        help="Maximum bytes to read per poll.",
    )
    parser.add_argument(
        "--duration",
        type=float,
        help="Stop after this many seconds. Defaults to running until Ctrl+C.",
    )
    parser.add_argument(
        "--raw-output",
        type=Path,
        help="Optional file to append the raw bytes to.",
    )
    return parser


def find_default_port(
    patterns: Iterable[str] = DEFAULT_PORT_PATTERNS,
    globber: Callable[[str], list[str]] = glob.glob,
) -> str | None:
    for pattern in patterns:
        matches = sorted(globber(pattern))
        if matches:
            return matches[0]
    return None


def render_ascii(data: bytes) -> str:
    return "".join(chr(byte) if chr(byte) in string.printable[:-5] else "." for byte in data)


def format_chunk(data: bytes, timestamp: datetime | None = None) -> str:
    current_time = timestamp or datetime.now()
    hex_dump = " ".join(f"{byte:02X}" for byte in data)
    ascii_dump = render_ascii(data)
    return f"{current_time.isoformat(timespec='milliseconds')} len={len(data):03d} hex=[{hex_dump}] ascii=[{ascii_dump}]"


def open_serial(port: str, baudrate: int, timeout: float):
    try:
        import serial
    except ImportError as exc:
        raise SystemExit(
            "pyserial is required. Install it in your venv with `pip install pyserial`."
        ) from exc

    return serial.Serial(
        port=port,
        baudrate=baudrate,
        bytesize=8,
        parity="N",
        stopbits=1,
        timeout=timeout,
        xonxoff=False,
        rtscts=False,
        dsrdtr=False,
    )


def capture_loop(
    connection,
    *,
    read_size: int,
    duration: float | None,
    output_stream,
    raw_output_path: Path | None,
) -> None:
    deadline = None if duration is None else time.monotonic() + duration

    raw_handle = None
    if raw_output_path is not None:
        raw_output_path.parent.mkdir(parents=True, exist_ok=True)
        raw_handle = raw_output_path.open("ab")

    try:
        while True:
            if deadline is not None and time.monotonic() >= deadline:
                return

            payload = connection.read(read_size)
            if not payload:
                continue

            print(format_chunk(payload), file=output_stream, flush=True)

            if raw_handle is not None:
                raw_handle.write(payload)
                raw_handle.flush()
    finally:
        if raw_handle is not None:
            raw_handle.close()


def main() -> int:
    args = build_parser().parse_args()
    port = args.port or find_default_port()

    if port is None:
        print(
            "No USB serial adapter was detected. Pass --port explicitly if needed.",
            file=sys.stderr,
        )
        return 1

    print(
        f"Capturing from {port} at {args.baudrate} baud (8N1). Press Ctrl+C to stop.",
        flush=True,
    )

    try:
        with open_serial(port, args.baudrate, args.timeout) as connection:
            capture_loop(
                connection,
                read_size=args.read_size,
                duration=args.duration,
                output_stream=sys.stdout,
                raw_output_path=args.raw_output,
            )
    except KeyboardInterrupt:
        print("\nStopped.", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
