"""Agent probe cycle: an empty pull does not submit probeResults."""

from __future__ import annotations

import unittest

import probe_cycle


class NoTasksTests(unittest.TestCase):
    def test_no_tasks_posts_one_heartbeat_without_probe_results(self) -> None:
        posts: list[dict] = []

        def poster(payload: dict) -> dict:
            posts.append(payload)
            return {"data": {"probeTasks": []}}

        def runner(task: dict) -> probe_cycle.CommandOutcome:
            raise AssertionError(f"runner must not be called for {task}")

        results = probe_cycle.pull_and_report(
            "http://127.0.0.1",
            "agent-empty",
            "host-empty",
            runner,
            poster=poster,
        )

        self.assertEqual([], results)
        self.assertEqual(1, len(posts))
        self.assertNotIn("probeResults", posts[0])
        self.assertNotIn("snapshot", posts[0])
        self.assertNotIn("judgment", posts[0])
        self.assertNotIn("kind", posts[0])


if __name__ == "__main__":
    unittest.main()
