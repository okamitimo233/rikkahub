#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Codeagent Bridge - Trellis context adapter for codeagent-wrapper.

Reads Trellis task context (JSONL files, prd.md) and constructs
codeagent-wrapper commands with proper file references.

Usage:
    python3 codeagent_bridge.py run <phase> [--backend codex|gemini] [--task-dir <dir>] [--extra-prompt <text>]
    python3 codeagent_bridge.py preview <phase> [--backend codex|gemini] [--task-dir <dir>]

Phases: implement, check, debug, research
Backends: codex (default), gemini

Examples:
    python3 codeagent_bridge.py run implement --backend codex
    python3 codeagent_bridge.py run implement --backend gemini --extra-prompt "Focus on UI components"
    python3 codeagent_bridge.py preview implement  # Show command without executing
"""

from __future__ import annotations

import sys

# Force UTF-8 on Windows
if sys.platform == "win32":
    import io as _io
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    elif hasattr(sys.stdout, "detach"):
        sys.stdout = _io.TextIOWrapper(
            sys.stdout.detach(), encoding="utf-8", errors="replace"
        )

import argparse
import json
import os
import subprocess
from pathlib import Path

# Reuse Trellis common utilities
sys.path.insert(0, str(Path(__file__).parent))
from common.paths import get_repo_root, get_current_task


# =============================================================================
# Constants
# =============================================================================

VALID_BACKENDS = ("codex", "gemini")
VALID_PHASES = ("implement", "check", "debug", "research")
DEFAULT_BACKEND = "codex"
DEFAULT_TIMEOUT = 7200000  # 2 hours in ms


# =============================================================================
# Context Reading (mirrors inject-subagent-context.py logic)
# =============================================================================


def read_jsonl_file_refs(repo_root: Path, jsonl_path: Path) -> list[str]:
    """
    Read JSONL file and extract file paths as @file references.

    Returns list of relative file paths referenced in the JSONL.
    """
    if not jsonl_path.is_file():
        return []

    refs = []
    try:
        for line in jsonl_path.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if not line:
                continue
            try:
                item = json.loads(line)
                file_path = item.get("file") or item.get("path")
                entry_type = item.get("type", "file")
                if not file_path:
                    continue

                if entry_type == "directory":
                    # Expand directory to individual .md files
                    dir_full = repo_root / file_path
                    if dir_full.is_dir():
                        for md_file in sorted(dir_full.glob("*.md")):
                            refs.append(str(md_file.relative_to(repo_root)))
                else:
                    full = repo_root / file_path
                    if full.is_file():
                        refs.append(file_path)
            except json.JSONDecodeError:
                continue
    except Exception:
        pass

    return refs


def get_phase_context(
    repo_root: Path, task_dir: str, phase: str
) -> tuple[list[str], str]:
    """
    Get file references and task prompt for a given phase.

    Returns:
        (file_refs, task_description)
    """
    task_path = repo_root / task_dir
    file_refs: list[str] = []

    # 1. Read phase-specific JSONL (fallback to spec.jsonl)
    phase_jsonl = task_path / f"{phase}.jsonl"
    if phase_jsonl.is_file():
        file_refs.extend(read_jsonl_file_refs(repo_root, phase_jsonl))
    else:
        spec_jsonl = task_path / "spec.jsonl"
        file_refs.extend(read_jsonl_file_refs(repo_root, spec_jsonl))

    # 2. Add prd.md
    prd_path = task_path / "prd.md"
    if prd_path.is_file():
        file_refs.append(f"{task_dir}/prd.md")

    # 3. Add info.md (for implement/debug)
    if phase in ("implement", "debug"):
        info_path = task_path / "info.md"
        if info_path.is_file():
            file_refs.append(f"{task_dir}/info.md")

    # 4. Build task description based on phase
    prd_content = ""
    if prd_path.is_file():
        try:
            prd_content = prd_path.read_text(encoding="utf-8")
        except Exception:
            pass

    task_desc = build_phase_prompt(phase, prd_content, task_dir)

    return file_refs, task_desc


def build_phase_prompt(phase: str, prd_content: str, task_dir: str) -> str:
    """Build task prompt for codeagent based on phase."""

    # Extract goal from PRD (first ## Goal section)
    goal = ""
    if prd_content:
        lines = prd_content.splitlines()
        in_goal = False
        goal_lines = []
        for line in lines:
            if line.strip().startswith("## Goal"):
                in_goal = True
                continue
            if in_goal and line.strip().startswith("## "):
                break
            if in_goal:
                goal_lines.append(line)
        goal = "\n".join(goal_lines).strip()

    if phase == "implement":
        return f"""Implement the following requirements. Read all referenced spec files first.

Task: {goal or 'See prd.md for full requirements'}

Rules:
- Follow all coding guidelines in the referenced spec files
- Do NOT make git commits
- Report all modified/created files when done"""

    elif phase == "check":
        return f"""Review the recent code changes against the referenced spec files.

Task: Check code quality for task in {task_dir}

Steps:
1. Run `git diff --name-only` to see changed files
2. Check each file against the referenced guidelines
3. Fix any issues found directly (don't just report)
4. Run lint/typecheck to verify"""

    elif phase == "debug":
        return f"""Fix the issues found in the code for task in {task_dir}.

Steps:
1. Read the referenced spec files and review output
2. Locate code that needs fixing
3. Apply precise fixes
4. Run typecheck to verify fixes
5. Do NOT make git commits"""

    elif phase == "research":
        return f"""Research the codebase to answer the following:

{goal or 'See prd.md for research questions'}

Output a structured report with:
- Files found (with paths)
- Code pattern analysis
- Related spec documents"""

    return goal or "See prd.md"


# =============================================================================
# Command Building
# =============================================================================


def build_codeagent_command(
    backend: str,
    file_refs: list[str],
    task_desc: str,
    working_dir: str = ".",
    extra_prompt: str = "",
) -> str:
    """
    Build the codeagent-wrapper command string.

    Converts Trellis file references to @file syntax in the HEREDOC body.
    """
    # Build file reference section
    ref_lines = []
    for ref in file_refs:
        ref_lines.append(f"@{ref}")

    refs_block = "\n".join(ref_lines) if ref_lines else ""

    # Combine task description
    full_task = task_desc
    if extra_prompt:
        full_task += f"\n\nAdditional instructions:\n{extra_prompt}"
    if refs_block:
        full_task += f"\n\nReference files:\n{refs_block}"

    cmd = f"""codeagent-wrapper --backend {backend} - {working_dir} <<'EOF'
{full_task}
EOF"""

    return cmd


# =============================================================================
# Main
# =============================================================================


def cmd_preview(args: argparse.Namespace) -> None:
    """Preview the codeagent command without executing."""
    repo_root = get_repo_root()

    # Resolve task directory
    task_dir = args.task_dir or get_current_task(repo_root)
    if not task_dir:
        print("Error: No active task. Use --task-dir or activate a task first.", file=sys.stderr)
        sys.exit(1)

    task_path = repo_root / task_dir
    if not task_path.is_dir():
        print(f"Error: Task directory not found: {task_dir}", file=sys.stderr)
        sys.exit(1)

    file_refs, task_desc = get_phase_context(repo_root, task_dir, args.phase)
    cmd = build_codeagent_command(
        backend=args.backend,
        file_refs=file_refs,
        task_desc=task_desc,
        working_dir=".",
        extra_prompt=args.extra_prompt or "",
    )

    print(f"# Backend: {args.backend}")
    print(f"# Phase: {args.phase}")
    print(f"# Task: {task_dir}")
    print(f"# File refs: {len(file_refs)}")
    print(f"# ---")
    print(cmd)


def cmd_run(args: argparse.Namespace) -> None:
    """Build and execute the codeagent command."""
    repo_root = get_repo_root()

    # Resolve task directory
    task_dir = args.task_dir or get_current_task(repo_root)
    if not task_dir:
        print("Error: No active task. Use --task-dir or activate a task first.", file=sys.stderr)
        sys.exit(1)

    task_path = repo_root / task_dir
    if not task_path.is_dir():
        print(f"Error: Task directory not found: {task_dir}", file=sys.stderr)
        sys.exit(1)

    file_refs, task_desc = get_phase_context(repo_root, task_dir, args.phase)
    cmd = build_codeagent_command(
        backend=args.backend,
        file_refs=file_refs,
        task_desc=task_desc,
        working_dir=".",
        extra_prompt=args.extra_prompt or "",
    )

    print(f"[codeagent-bridge] Running {args.phase} with {args.backend} backend...")
    print(f"[codeagent-bridge] Task: {task_dir}")
    print(f"[codeagent-bridge] File refs: {len(file_refs)}")

    # Execute
    try:
        result = subprocess.run(
            cmd,
            shell=True,
            cwd=str(repo_root),
            timeout=args.timeout,
        )
        sys.exit(result.returncode)
    except subprocess.TimeoutExpired:
        print(f"\n[codeagent-bridge] Timeout after {args.timeout}s", file=sys.stderr)
        sys.exit(124)
    except KeyboardInterrupt:
        print("\n[codeagent-bridge] Interrupted", file=sys.stderr)
        sys.exit(130)


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Codeagent Bridge - Trellis context adapter"
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    # Common arguments
    def add_common_args(p: argparse.ArgumentParser) -> None:
        p.add_argument("phase", choices=VALID_PHASES, help="Execution phase")
        p.add_argument(
            "--backend",
            choices=VALID_BACKENDS,
            default=DEFAULT_BACKEND,
            help=f"AI backend (default: {DEFAULT_BACKEND})",
        )
        p.add_argument("--task-dir", help="Task directory (default: current task)")
        p.add_argument("--extra-prompt", help="Additional prompt text")

    # run
    run_parser = subparsers.add_parser("run", help="Execute codeagent")
    add_common_args(run_parser)
    run_parser.add_argument(
        "--timeout",
        type=int,
        default=7200,
        help="Timeout in seconds (default: 7200)",
    )

    # preview
    preview_parser = subparsers.add_parser("preview", help="Preview command")
    add_common_args(preview_parser)

    args = parser.parse_args()

    if args.command == "run":
        cmd_run(args)
    elif args.command == "preview":
        cmd_preview(args)


if __name__ == "__main__":
    main()
