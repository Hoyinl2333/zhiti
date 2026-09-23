import importlib.util
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path


MODULE_PATH = Path(__file__).parents[1] / "build_content.py"
SPEC = importlib.util.spec_from_file_location("build_content", MODULE_PATH)
PIPELINE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader
sys.modules[SPEC.name] = PIPELINE
SPEC.loader.exec_module(PIPELINE)


class PipelineTest(unittest.TestCase):
    def test_option_answer_is_removed_from_content(self):
        rows, answers = PIPELINE.option_rows("- A. 甲\n- B. 乙 ✅\n- C. 丙\n- D. 丁")
        self.assertEqual(answers, ["B"])
        self.assertEqual(rows[1], ("B", "乙"))

    def test_script_is_removed(self):
        self.assertEqual(PIPELINE.clean_text("<p>题干</p><script>bad()</script>"), "题干")

    def test_zip_has_fixed_timestamp_and_sorted_paths(self):
        with tempfile.TemporaryDirectory() as temp_name:
            root = Path(temp_name)
            (root / "source").mkdir()
            (root / "source" / "b").write_text("b")
            (root / "source" / "a").write_text("a")
            output = root / "pack.zip"
            PIPELINE.write_deterministic_zip(root / "source", output)
            with zipfile.ZipFile(output) as archive:
                self.assertEqual(archive.namelist(), ["a", "b"])
                self.assertTrue(all(item.date_time == (2020, 1, 1, 0, 0, 0) for item in archive.infolist()))


if __name__ == "__main__":
    unittest.main()
