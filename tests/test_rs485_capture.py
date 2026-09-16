from __future__ import annotations

import io
import unittest
from datetime import datetime
from pathlib import Path
from tempfile import TemporaryDirectory

from tools.rs485_capture import capture_loop, find_default_port, format_chunk


class FakeSerial:
    def __init__(self, payloads: list[bytes]) -> None:
        self._payloads = list(payloads)

    def read(self, _: int) -> bytes:
        if self._payloads:
            return self._payloads.pop(0)
        raise KeyboardInterrupt


class Rs485CaptureTests(unittest.TestCase):
    def test_find_default_port_returns_first_match_from_patterns(self) -> None:
        matches = {
            "/dev/cu.usbserial*": ["/dev/cu.usbserial-B"],
            "/dev/cu.usbmodem*": ["/dev/cu.usbmodem-A"],
        }

        port = find_default_port(globber=lambda pattern: matches.get(pattern, []))

        self.assertEqual("/dev/cu.usbserial-B", port)

    def test_format_chunk_renders_hex_and_ascii(self) -> None:
        line = format_chunk(b"A\x00Z", timestamp=datetime(2026, 4, 10, 12, 0, 0, 123000))

        self.assertEqual(
            "2026-04-10T12:00:00.123 len=003 hex=[41 00 5A] ascii=[A.Z]",
            line,
        )

    def test_capture_loop_writes_stdout_and_raw_output(self) -> None:
        fake_serial = FakeSerial([b"\x01\x02"])
        stdout = io.StringIO()

        with TemporaryDirectory() as temp_dir:
            raw_output = Path(temp_dir) / "capture.bin"

            with self.assertRaises(KeyboardInterrupt):
                capture_loop(
                    fake_serial,
                    read_size=64,
                    duration=None,
                    output_stream=stdout,
                    raw_output_path=raw_output,
                )

            self.assertIn("len=002 hex=[01 02] ascii=[..]", stdout.getvalue())
            self.assertEqual(b"\x01\x02", raw_output.read_bytes())


if __name__ == "__main__":
    unittest.main()
