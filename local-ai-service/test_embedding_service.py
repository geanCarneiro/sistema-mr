import unittest
import pathlib
import sys
from unittest.mock import patch

sys.path.insert(0, str(pathlib.Path(__file__).parent))


class ValidationTest(unittest.TestCase):
    def test_validation_rejects_empty_texts(self):
        with patch.dict("os.environ", {"EMBEDDING_MODEL": "test"}):
            import embedding_service

            with self.assertRaises(ValueError):
                embedding_service.validate_texts([])

    def test_validation_accepts_batch(self):
        with patch.dict("os.environ", {"EMBEDDING_MODEL": "test"}):
            import embedding_service

            self.assertEqual(["a", "b"], embedding_service.validate_texts(["a", "b"]))

    def test_message_validation_accepts_conversational_roles(self):
        import embedding_service

        messages = embedding_service.validate_messages([
            {"role": "system", "content": "Você é local."},
            {"role": "user", "content": "Analise isto."},
        ])

        self.assertEqual(2, len(messages))

    def test_message_validation_rejects_unknown_role(self):
        import embedding_service

        with self.assertRaises(ValueError):
            embedding_service.validate_messages([{"role": "developer", "content": "inválido"}])


if __name__ == "__main__":
    unittest.main()
