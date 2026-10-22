#!/usr/bin/env python3
"""RAG 评测脚本：逐题调用检索诊断接口 + 阻塞问答接口，落盘召回与回答。

用法：
  python run_eval.py --top-k 4 --threshold 0.5
  python run_eval.py --retrieval-only --top-k 5 --threshold 0.4
"""

import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

BASE_URL = os.environ.get("AI_BASE_URL", "http://127.0.0.1:8088")
IDENTITY = "rag-eval"


def request(method, path, payload=None, timeout=180):
    data = None if payload is None else json.dumps(payload, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(BASE_URL + path, data=data, method=method, headers={
        "Content-Type": "application/json; charset=utf-8",
        "X-Identity-Id": IDENTITY,
    })
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8"))


def retrieve(question, top_k, threshold):
    query = urllib.parse.urlencode({"q": question, "topK": top_k, "threshold": threshold})
    body = request("GET", f"/ai/drug/debug/retrieve?{query}")
    if body.get("code") != 1:
        raise RuntimeError(body.get("msg"))
    return body.get("data") or []


def chat(question):
    payload = {"message": question, "conversationId": "eval-" + uuid.uuid4().hex[:16]}
    body = request("POST", "/ai/drug/chat", payload)
    if body.get("code") != 1:
        raise RuntimeError(body.get("msg"))
    return (body.get("data") or {}).get("answer", "")


def doc_name(source):
    return (source or "").replace("\\", "/").rsplit("/", 1)[-1]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--questions", default=os.path.join(os.path.dirname(__file__), "questions.json"))
    parser.add_argument("--top-k", type=int, default=4)
    parser.add_argument("--threshold", type=float, default=0.5)
    parser.add_argument("--retrieval-only", action="store_true")
    parser.add_argument("--out", default=None)
    parser.add_argument("--sleep", type=float, default=0.5)
    args = parser.parse_args()

    with open(args.questions, encoding="utf-8") as f:
        questions = json.load(f)

    results = []
    for idx, item in enumerate(questions, 1):
        print(f"[{idx}/{len(questions)}] {item['id']} {item['question']}", flush=True)
        row = dict(item)
        try:
            chunks = retrieve(item["question"], args.top_k, args.threshold)
            row["retrieved"] = [
                {"source": doc_name(c.get("source")), "score": round(c.get("score") or 0, 4),
                 "text": (c.get("text") or "")[:120]}
                for c in chunks
            ]
            expected = set(item.get("expected_docs") or [])
            hit_sources = {r["source"] for r in row["retrieved"]}
            row["hit_at_k"] = bool(expected & hit_sources) if expected else None
            row["answer"] = "" if args.retrieval_only else chat(item["question"])
        except (urllib.error.URLError, TimeoutError, RuntimeError) as e:
            row["error"] = str(e)
            row["answer"] = ""
            row["hit_at_k"] = False
        results.append(row)
        print("    docs:", ", ".join(f"{r['source']}({r['score']})" for r in row.get("retrieved", []))
              or "无", "| hit:", row.get("hit_at_k"), flush=True)
        if not args.retrieval_only:
            print("    answer:", (row.get("answer") or "")[:160].replace("\n", " "), flush=True)
        time.sleep(args.sleep)

    answerable = [r for r in results if r["category"] in ("fact", "platform")]
    hits = sum(1 for r in answerable if r.get("hit_at_k"))
    summary = {
        "top_k": args.top_k,
        "threshold": args.threshold,
        "retrieval_only": args.retrieval_only,
        "questions": len(results),
        "answerable": len(answerable),
        "hit_at_k": hits,
        "hit_rate_at_k": round(hits / len(answerable), 4) if answerable else 0,
        "errors": [r["id"] for r in results if r.get("error")],
    }
    out = args.out or os.path.join(os.path.dirname(__file__),
                                   f"results-topk{args.top_k}-thr{args.threshold}{'-retrieval' if args.retrieval_only else ''}.json")
    with open(out, "w", encoding="utf-8") as f:
        json.dump({"summary": summary, "results": results}, f, ensure_ascii=False, indent=2)
    print("\nSUMMARY:", json.dumps(summary, ensure_ascii=False), flush=True)
    print("saved:", out, flush=True)


if __name__ == "__main__":
    sys.exit(main())
