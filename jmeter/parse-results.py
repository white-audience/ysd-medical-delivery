#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""药速达 JMeter 结果解析脚本。

读取 jmeter/results/*.jtl，按计划汇总并输出表格：
    计划名 | 请求数 | 平均 | P50 | P95 | P99 | 错误率

用法：
    python parse-results.py                 # 解析脚本同级的 results/ 目录
    python parse-results.py <结果目录>       # 解析指定目录（可含多个 .jtl）
    python parse-results.py <单个.jtl>       # 只解析一个文件

说明：
    - .jtl 为非 GUI 模式默认的 CSV 格式，字段含 elapsed（响应毫秒）、success、label 等。
    - 错误率 = success=false 的样本数 / 总样本数。
    - P50/P95/P99 用线性插值百分位，与 JMeter HTML 报告口径接近但不保证完全一致。
    - 时间单位：毫秒（ms）。
"""

import csv
import glob
import os
import sys


def percentile(sorted_vals, pct):
    """线性插值百分位。sorted_vals 需已升序。"""
    n = len(sorted_vals)
    if n == 0:
        return 0.0
    if n == 1:
        return float(sorted_vals[0])
    k = (n - 1) * (pct / 100.0)
    f = int(k)
    c = min(f + 1, n - 1)
    if f == c:
        return float(sorted_vals[f])
    return sorted_vals[f] + (sorted_vals[c] - sorted_vals[f]) * (k - f)


def pick(columns, *names):
    """在列名中按候选列表找一个存在的列（大小写不敏感）。"""
    lower = {c.lower(): c for c in columns}
    for name in names:
        if name.lower() in lower:
            return lower[name.lower()]
    return None


def parse_file(path):
    """解析单个 .jtl，返回 (请求数, 平均, P50, P95, P99, 错误率字符串) 或 None。"""
    elapsed_col = None
    success_col = None
    total = 0
    failures = 0
    values = []

    with open(path, "r", encoding="utf-8-sig", newline="") as fh:
        reader = csv.DictReader(fh)
        if not reader.fieldnames:
            return None
        for row in reader:
            if elapsed_col is None:
                elapsed_col = pick(reader.fieldnames, "elapsed", "Latency")
                success_col = pick(reader.fieldnames, "success", "Failure")
            total += 1
            raw = (row.get(elapsed_col) or "").strip() if elapsed_col else ""
            try:
                values.append(float(raw))
            except ValueError:
                pass
            ok = (row.get(success_col) or "").strip().lower() if success_col else "true"
            if ok == "false":
                failures += 1

    if total == 0:
        return None

    values.sort()
    avg = sum(values) / len(values) if values else 0.0
    err_rate = failures * 100.0 / total
    return (
        total,
        avg,
        percentile(values, 50),
        percentile(values, 95),
        percentile(values, 99),
        "{:.2f}%".format(err_rate),
    )


def collect_jtl(target):
    if os.path.isfile(target):
        return [target]
    if os.path.isdir(target):
        return sorted(glob.glob(os.path.join(target, "*.jtl")))
    return []


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    target = sys.argv[1] if len(sys.argv) > 1 else os.path.join(here, "results")

    files = collect_jtl(target)
    if not files:
        print("未找到 .jtl 文件：{}".format(target))
        print("请先运行 run-batch1.bat / run-batch2.bat / run-all.bat 生成结果。")
        return 1

    rows = []
    for path in files:
        name = os.path.splitext(os.path.basename(path))[0]
        try:
            stats = parse_file(path)
        except Exception as exc:  # noqa: BLE001
            rows.append((name, "解析失败: {}".format(exc)))
            continue
        if stats is None:
            rows.append((name, "空文件 / 无样本"))
            continue
        total, avg, p50, p95, p99, err = stats
        rows.append(
            (
                name,
                "{}|{:.1f}|{:.1f}|{:.1f}|{:.1f}|{}".format(total, avg, p50, p95, p99, err),
            )
        )

    headers = ["计划名", "请求数", "平均(ms)", "P50(ms)", "P95(ms)", "P99(ms)", "错误率"]
    table = []
    for name, payload in rows:
        if "|" in payload and not payload.startswith(("解析失败", "空文件")):
            total, avg, p50, p95, p99, err = payload.split("|")
            table.append([name, total, avg, p50, p95, p99, err])
        else:
            table.append([name, payload, "-", "-", "-", "-", "-"])

    widths = [len(h) for h in headers]
    for row in table:
        for i, cell in enumerate(row):
            widths[i] = max(widths[i], len(cell))

    def fmt(cells):
        return "  ".join(c.ljust(widths[i]) for i, c in enumerate(cells))

    line = "-+-".join("-" * w for w in widths)
    print(fmt(headers))
    print(line)
    for row in table:
        print(fmt(row))

    print("")
    print("共 {} 个计划，结果目录：{}".format(len(table), target))
    print("提示：单文件明细见对应 reports/<name>/index.html。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
