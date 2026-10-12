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


class SnapshotReportTests(unittest.TestCase):
    def test_result_post_omits_snapshot(self) -> None:
        posts: list[dict] = []

        def poster(payload: dict) -> dict:
            posts.append(payload)
            if len(posts) == 1:
                return {
                    "data": {
                        "probeTasks": [
                            {
                                "id": "task-snap",
                                "command": "printf",
                                "intervalSeconds": 60,
                                "curatedExpected": {"up": True},
                            }
                        ]
                    }
                }
            return {"data": {"probeTasks": []}}

        def runner(task: dict) -> probe_cycle.CommandOutcome:
            return probe_cycle.CommandOutcome('{"up": true}', "", 0, False)

        probe_cycle.pull_and_report(
            "http://127.0.0.1",
            "agent-snap",
            "host-snap",
            runner,
            snapshot={"containers": [{"runtimeId": "docker-runtime", "name": "app", "labels": {}}]},
            poster=poster,
        )

        self.assertEqual(2, len(posts))
        self.assertIn("snapshot", posts[0])
        self.assertNotIn("probeResults", posts[0])
        self.assertNotIn("snapshot", posts[1])
        self.assertEqual("task-snap", posts[1]["probeResults"][0]["taskId"])
        self.assertEqual('{"up": true}', posts[1]["probeResults"][0]["evidence"])
        self.assertNotIn("judgment", posts[1]["probeResults"][0])
        self.assertNotIn("kind", posts[1]["probeResults"][0])


class SilentFailureTests(unittest.TestCase):
    def test_silent_nonzero_exit_keeps_empty_evidence(self) -> None:
        result = probe_cycle.result_for_task(
            {"id": "task-silent"},
            probe_cycle.CommandOutcome("", "", 1, False),
        )
        self.assertIsNotNone(result)
        self.assertEqual("task-silent", result["taskId"])
        self.assertFalse(result["readable"])
        self.assertEqual("", result["evidence"])
        self.assertNotIn("value", result)
        self.assertNotIn("kind", result)


class SubprocessRunnerTests(unittest.TestCase):
    def test_task_interval_does_not_kill_the_command(self) -> None:
        outcome = probe_cycle.subprocess_runner(
            {
                "command": "sleep 2; printf '{\"up\":true}'",
                "intervalSeconds": 1,
            }
        )
        self.assertFalse(outcome.timed_out)
        self.assertEqual(0, outcome.exit_code)
        self.assertEqual('{"up":true}', outcome.stdout)


class UnreachableControlPlaneTests(unittest.TestCase):
    def test_unreachable_control_plane_is_a_probe_cycle_error(self) -> None:
        with self.assertRaises(probe_cycle.ProbeCycleError):
            probe_cycle.post_heartbeat("http://127.0.0.1:1", {"agentId": "a", "hostId": "h"})


if __name__ == "__main__":
    unittest.main()
