import unittest
from scripts.validate_config import validate

class ConfigurationTests(unittest.TestCase):
    def test_all_profiles(self):
        validate()

if __name__ == "__main__":
    unittest.main()
