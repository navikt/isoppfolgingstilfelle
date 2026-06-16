#!/usr/bin/env python3
"""
Compare candidates from KANDIDAT_UTEN_ARBEIDSGIVER (DB CSV export)
with the expected candidates from a simple CSV file containing only aktor_id.

The comparison is centered on oversendt_at — i.e. which candidates were
actually sent — rather than just DB existence.

Usage:
    python compare_kandidater_csv.py <db_export.csv> <expected.csv> [--only SECTION ...] [--skip SECTION ...]

Sections:
    summary             Counts and percentages for all categories
    sent-not-expected   Sent (oversendt_at set) but aktor_id not in expected CSV
    expected-not-sent   In expected CSV but no sent DB row for this aktor_id
    in-db-not-sent      aktor_id in both DB and expected CSV, but oversendt_at is null

Export query (GCP Cloud SQL / psql):
    SELECT aktor_id, status, tilfelle_start, oversendt_at
    FROM KANDIDAT_UTEN_ARBEIDSGIVER
    WHERE created_at <= '2026-06-09'
    ORDER BY created_at;

Expected CSV format:
    aktor_id
    1234567890123
    9876543210987
    ...
"""

import argparse
import csv
from collections import defaultdict

SECTIONS = ["summary", "sent-not-expected", "expected-not-sent", "in-db-not-sent"]


def load_db_csv(path: str) -> dict[str, list[dict]]:
    entries = defaultdict(list)
    with open(path, newline="", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            entries[row["aktor_id"]].append(row)
    return entries


def load_expected_csv(path: str) -> set[str]:
    ids = set()
    with open(path, newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            aktor_id = row.get("aktor_id", "").strip()
            if aktor_id:
                ids.add(aktor_id)
    return ids


def has_oversendt(row: dict) -> bool:
    return bool(row.get("oversendt_at", "").strip())


def pct(count: int, total: int) -> str:
    return f"{count / total * 100:.1f}%" if total else "n/a"


def main():
    parser = argparse.ArgumentParser(description="Compare DB kandidater with expected aktor_id CSV.")
    parser.add_argument("db_csv", help="Path to DB CSV export")
    parser.add_argument("expected_csv", help="Path to expected aktor_id CSV file")
    parser.add_argument(
        "--only",
        nargs="+",
        choices=SECTIONS,
        metavar="SECTION",
        help=f"Only print these sections (space-separated). Choices: {', '.join(SECTIONS)}",
    )
    parser.add_argument(
        "--skip",
        nargs="+",
        choices=SECTIONS,
        metavar="SECTION",
        help=f"Skip these sections (space-separated). Choices: {', '.join(SECTIONS)}",
    )
    args = parser.parse_args()

    def show(section: str) -> bool:
        if args.only:
            return section in args.only
        if args.skip:
            return section not in args.skip
        return True

    db = load_db_csv(args.db_csv)
    expected_ids = load_expected_csv(args.expected_csv)

    db_ids = set(db.keys())

    # Partition DB rows by whether they were sent
    sent_by_aktor: dict[str, list[dict]] = defaultdict(list)
    unsent_by_aktor: dict[str, list[dict]] = defaultdict(list)
    for aktor_id, rows in db.items():
        for row in rows:
            if has_oversendt(row):
                sent_by_aktor[aktor_id].append(row)
            else:
                unsent_by_aktor[aktor_id].append(row)

    sent_ids = set(sent_by_aktor.keys())

    # Core categories
    matched_sent = expected_ids & sent_ids            # in expected CSV and sent ✅
    sent_not_expected = sent_ids - expected_ids       # sent, but not in expected CSV
    expected_not_sent = expected_ids - sent_ids       # expected, but never sent
    in_db_not_sent = (expected_ids & db_ids) - sent_ids  # in expected + in DB, but not sent
    not_in_db_at_all = expected_not_sent - db_ids    # expected, never created in DB

    if show("summary"):
        n_exp = len(expected_ids)
        n_sent = len(sent_ids)
        print("=== Summary ===")
        print(f"  Total in DB:                        {len(db_ids)}")
        print(f"  Total sent (oversendt_at set):       {n_sent}  ({pct(n_sent, len(db_ids))} of DB)")
        print(f"  Total in expected CSV:               {n_exp}")
        print()
        print(f"  Sent AND expected (matched):         {len(matched_sent)}  ({pct(len(matched_sent), n_exp)} of expected)")
        print(f"  Sent but NOT expected:               {len(sent_not_expected)}  ({pct(len(sent_not_expected), n_sent)} of sent)")
        print(f"  Expected but NOT sent:               {len(expected_not_sent)}  ({pct(len(expected_not_sent), n_exp)} of expected)")
        print(f"    └─ in DB but not sent:             {len(in_db_not_sent)}  ({pct(len(in_db_not_sent), len(expected_not_sent))} of not-sent)")
        print(f"    └─ not in DB at all:               {len(not_in_db_at_all)}  ({pct(len(not_in_db_at_all), len(expected_not_sent))} of not-sent)")

        if in_db_not_sent:
            status_counts: dict[str, int] = defaultdict(int)
            for aktor_id in in_db_not_sent:
                for row in unsent_by_aktor[aktor_id]:
                    status_counts[row["status"]] += 1
            print(f"      DB status breakdown for in-db-not-sent:")
            for status, count in sorted(status_counts.items(), key=lambda x: -x[1]):
                print(f"        {status}: {count}  ({pct(count, len(in_db_not_sent))})")

    if show("sent-not-expected") and sent_not_expected:
        print(f"\n=== Sent but NOT expected ({len(sent_not_expected)}) ===")
        for aktor_id in sorted(sent_not_expected):
            for row in sent_by_aktor[aktor_id]:
                print(f"  aktor_id={aktor_id}  oversendt_at={row['oversendt_at']}  status={row['status']}")

    if show("expected-not-sent") and expected_not_sent:
        print(f"\n=== Expected but NOT sent ({len(expected_not_sent)}) ===")
        for aktor_id in sorted(expected_not_sent):
            in_db = aktor_id in db_ids
            db_status = db[aktor_id][0]["status"] if in_db else "not in DB"
            print(f"  aktor_id={aktor_id}  db_status={db_status}")

    if show("in-db-not-sent") and in_db_not_sent:
        print(f"\n=== In DB + in expected CSV but NOT sent ({len(in_db_not_sent)}) ===")
        for aktor_id in sorted(in_db_not_sent):
            for row in unsent_by_aktor[aktor_id]:
                print(f"  aktor_id={aktor_id}  status={row['status']}  tilfelle_start={row['tilfelle_start']}")


if __name__ == "__main__":
    main()
