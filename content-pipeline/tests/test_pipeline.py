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

VERIFY_SPEC = importlib.util.spec_from_file_location("verify_pack", Path(__file__).parents[1] / "verify_pack.py")
VERIFY = importlib.util.module_from_spec(VERIFY_SPEC)
assert VERIFY_SPEC.loader
sys.modules[VERIFY_SPEC.name] = VERIFY
VERIFY_SPEC.loader.exec_module(VERIFY)


class PipelineTest(unittest.TestCase):
    def test_option_answer_is_removed_from_content(self):
        rows, answers = PIPELINE.option_rows("- A. 甲\n- B. 乙 ✅\n- C. 丙\n- D. 丁")
        self.assertEqual(answers, ["B"])
        self.assertEqual(rows[1], ("B", "乙"))

    def test_script_is_removed(self):
        self.assertEqual(PIPELINE.clean_text("<p>题干</p><script>bad()</script>"), "题干")

    def test_fast_solution_is_separate_from_reasoning(self):
        source = "## 推理链\n1. 第一步\n\n**最快解法**：直接排除。\n\n## 易错点\n- 看错题干"
        self.assertEqual(PIPELINE.heading_section(source, "推理链"), "1. 第一步")
        self.assertEqual(PIPELINE.fast_solution(source), "直接排除。")

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

    def test_zip_path_traversal_is_rejected(self):
        with tempfile.TemporaryDirectory() as temp_name:
            archive_path = Path(temp_name) / "unsafe.zip"
            with zipfile.ZipFile(archive_path, "w") as archive:
                archive.writestr("../escape", "bad")
            with zipfile.ZipFile(archive_path) as archive:
                with self.assertRaises(ValueError):
                    VERIFY.safe_members(archive)


if __name__ == "__main__":
    unittest.main()
