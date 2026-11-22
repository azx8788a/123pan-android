#!/usr/bin/env python3
"""Wait for a specific Android CI commit and download its artifacts. GH_TOKEN is environment-only."""

import argparse
import json
import os
from pathlib import Path
import shutil
import time
import urllib.error
import urllib.parse
import urllib.request
import zipfile


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", required=True)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--timeout", type=int, default=1500)
    args = parser.parse_args()
    token = os.environ.get("GH_TOKEN")
    if not token:
        raise SystemExit("GH_TOKEN is required in the process environment")
    headers = {"Authorization": "Bearer " + token, "Accept": "application/vnd.github+json", "X-GitHub-Api-Version": "2022-11-28"}
    base = "https://api.github.com/repos/" + args.repo
    opener = urllib.request.build_opener(NoRedirect)

    def api(path):
        request = urllib.request.Request(base + path, headers=headers)
        with urllib.request.urlopen(request, timeout=45) as response:
            return json.load(response)

    def archive(url, destination):
        try:
            response = opener.open(urllib.request.Request(url, headers=headers), timeout=60)
        except urllib.error.HTTPError as error:
            if error.code != 302:
                raise
            # Authorization is deliberately omitted from the artifact-storage redirect.
            response = urllib.request.urlopen(error.headers["Location"], timeout=120)
        with response, destination.open("wb") as output:
            shutil.copyfileobj(response, output, length=64 * 1024)

    deadline = time.monotonic() + args.timeout
    run = None
    previous = None
    while time.monotonic() < deadline:
        runs = api("/actions/workflows/android.yml/runs?branch=android&per_page=20").get("workflow_runs", [])
        matching = [item for item in runs if item.get("head_sha") == args.commit]
        if matching:
            run = matching[0]
            status = run.get("status")
            if status != previous:
                print(json.dumps({"run": run["id"], "status": status, "url": run["html_url"]}), flush=True)
                previous = status
            if status == "completed":
                break
        time.sleep(15)
    if not run or run.get("status") != "completed":
        raise SystemExit("CI wait timed out; inspect the Actions page before starting another build")
    output = Path(args.output) / str(run["id"])
    output.mkdir(parents=True, exist_ok=True)
    archive(base + "/actions/runs/" + str(run["id"]) + "/logs", output / "ci-logs.zip")
    with zipfile.ZipFile(output / "ci-logs.zip") as logs:
        for name in logs.namelist():
            path = output / "logs" / name
            if path.resolve().is_relative_to(output.resolve()):
                path.parent.mkdir(parents=True, exist_ok=True)
                if not name.endswith("/"):
                    path.write_bytes(logs.read(name))
    artifacts = api("/actions/runs/" + str(run["id"]) + "/artifacts").get("artifacts", [])
    downloaded = []
    for artifact in artifacts:
        if artifact.get("expired"):
            continue
        zip_path = output / (artifact["name"] + ".zip")
        archive(artifact["archive_download_url"], zip_path)
        directory = output / artifact["name"]
        directory.mkdir(exist_ok=True)
        with zipfile.ZipFile(zip_path) as bundle:
            for name in bundle.namelist():
                path = directory / name
                if path.resolve().is_relative_to(directory.resolve()):
                    path.parent.mkdir(parents=True, exist_ok=True)
                    if not name.endswith("/"):
                        with bundle.open(name) as source, path.open("wb") as target:
                            shutil.copyfileobj(source, target, length=64 * 1024)
        downloaded.append(str(directory))
    result = {"run": run["id"], "conclusion": run.get("conclusion"), "url": run["html_url"], "output": str(output), "artifacts": downloaded}
    (output / "result.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
    print(json.dumps(result), flush=True)
    if run.get("conclusion") != "success":
        raise SystemExit(1)


if __name__ == "__main__":
    main()
