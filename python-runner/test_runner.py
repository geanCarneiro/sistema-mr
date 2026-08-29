import unittest

from runner import execute_python


class ExecutePythonTests(unittest.TestCase):
    def test_executes_deterministic_calculation(self) -> None:
        result = execute_python("from decimal import Decimal\nprint(Decimal('0.1') + Decimal('0.2'))")

        self.assertEqual("0.3", result.output)
        self.assertEqual("", result.error)
        self.assertEqual(0, result.exitCode)
        self.assertFalse(result.timedOut)
        self.assertFalse(result.truncated)

    def test_returns_python_error(self) -> None:
        result = execute_python("raise ValueError('falha esperada')")

        self.assertIn("ValueError: falha esperada", result.error)
        self.assertNotEqual(0, result.exitCode)


if __name__ == "__main__":
    unittest.main()
