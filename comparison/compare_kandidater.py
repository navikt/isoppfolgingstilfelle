#!/usr/bin/env python3
"""
Compare candidates from KANDIDAT_UTEN_ARBEIDSGIVER (DB CSV export)
with the expected candidates from the Modia/AO JSON file.

The comparison is centered on oversendt_at — i.e. which candidates were
actually sent — rather than just DB existence.

Usage:
    python compare_kandidater.py <db_export.csv> <modiaao.json> [--only SECTION ...] [--skip SECTION ...]

Sections:
    summary             Counts and percentages for all categories
    sent-not-expected   Sent (oversendt_at set) but aktor_id not in JSON
    expected-not-sent   In JSON but no sent DB row for this aktor_id
    in-db-not-sent      aktor_id in both DB and JSON, but oversendt_at is null
    timing              Aggregate lag stats for correctly matched+sent candidates
    discrepancies       Cases where oppfølging_start_dato is BEFORE oversendt_at

Export query (GCP Cloud SQL / psql):
    SELECT aktor_id, status, tilfelle_start, oversendt_at
    FROM KANDIDAT_UTEN_ARBEIDSGIVER
    WHERE created_at <= '2026-06-09'
    ORDER BY created_at;
"""

import argparse
import csv
import json
from collections import defaultdict
from datetime import datetime, timezone

SECTIONS = ["summary", "sent-not-expected", "expected-not-sent", "in-db-not-sent", "timing", "discrepancies"]

DATETIME_FORMATS = [
    "%Y-%m-%d %H:%M:%S.%f",
    "%Y-%m-%d %H:%M:%S",
    "%Y-%m-%dT%H:%M:%S.%f%z",
    "%Y-%m-%dT%H:%M:%S%z",
]


def parse_dt(value: str) -> datetime | None:
    if not value:
        return None
    for fmt in DATETIME_FORMATS:
        try:
            dt = datetime.strptime(value.strip(), fmt)
            if dt.tzinfo is None:
                dt = dt.replace(tzinfo=timezone.utc)
            return dt
        except ValueError:
            continue
    return None


def load_db_csv(path: str) -> dict[str, list[dict]]:
    entries = defaultdict(list)
    with open(path, newline="", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            entries[row["aktor_id"]].append(row)
    return entries


def load_json(path: str) -> dict[str, list[dict]]:
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    entries = defaultdict(list)
    for item in data["data"]:
        entries[item["aktor_id"]].append(item)
    return entries


def pct(count: int, total: int) -> str:
    return f"{count / total * 100:.1f}%" if total else "n/a"


def main():
    parser = argparse.ArgumentParser(description="Compare DB kandidater with Modia/AO JSON.")
    parser.add_argument("db_csv", help="Path to DB CSV export")
    parser.add_argument("modiaao_json", help="Path to Modia/AO JSON file")
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
    expected = load_json(args.modiaao_json)

    db_ids = set(db.keys())
    expected_ids = set(expected.keys())

    # Partition DB rows by whether they were sent
    sent_by_aktor: dict[str, list[dict]] = defaultdict(list)
    unsent_by_aktor: dict[str, list[dict]] = defaultdict(list)
    for aktor_id, rows in db.items():
        for row in rows:
            if parse_dt(row.get("oversendt_at", "")):
                sent_by_aktor[aktor_id].append(row)
            else:
                unsent_by_aktor[aktor_id].append(row)

    sent_ids = set(sent_by_aktor.keys())

    # Core categories
    matched_sent = expected_ids & sent_ids            # in JSON and sent ✅
    sent_not_expected = sent_ids - expected_ids       # sent, but JSON didn't expect them
    expected_not_sent = expected_ids - sent_ids       # JSON expected, but never sent
    in_db_not_sent = (expected_ids & db_ids) - sent_ids  # in JSON + in DB, but not sent
    not_in_db_at_all = expected_not_sent - db_ids    # in JSON, never created in DB

    # Timing data for matched+sent candidates
    matched_timings = []
    discrepancies = []
    for aktor_id in matched_sent:
        oppfolging_dates = [parse_dt(e["oppfølging_start_dato"]) for e in expected[aktor_id]]
        oppfolging_dates = [d for d in oppfolging_dates if d is not None]
        oppfolging_start = min(oppfolging_dates) if oppfolging_dates else None

        for row in sent_by_aktor[aktor_id]:
            oversendt_at = parse_dt(row["oversendt_at"])
            if oversendt_at and oppfolging_start:
                diff_seconds = (oppfolging_start - oversendt_at).total_seconds()
                matched_timings.append((aktor_id, oversendt_at, oppfolging_start, diff_seconds))
                if diff_seconds < 0:
                    discrepancies.append((aktor_id, oversendt_at, oppfolging_start, diff_seconds))

    if show("summary"):
        n_exp = len(expected_ids)
        n_sent = len(sent_ids)
        print("=== Summary ===")
        print(f"  Total in DB:                        {len(db_ids)}")
        print(f"  Total sent (oversendt_at set):       {n_sent}  ({pct(n_sent, len(db_ids))} of DB)")
        print(f"  Total in JSON (expected):            {n_exp}")
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
            for entry in expected[aktor_id]:
                print(f"  aktor_id={aktor_id}  oppfølging_start_dato={entry['oppfølging_start_dato']}  db_status={db_status}")

    if show("in-db-not-sent") and in_db_not_sent:
        print(f"\n=== In DB + in JSON but NOT sent ({len(in_db_not_sent)}) ===")
        for aktor_id in sorted(in_db_not_sent):
            for row in unsent_by_aktor[aktor_id]:
                print(f"  aktor_id={aktor_id}  status={row['status']}  tilfelle_start={row['tilfelle_start']}")

    if show("timing") and matched_timings:
        diffs = [t[3] for t in matched_timings]
        avg_diff = sum(diffs) / len(diffs)
        max_entry = max(matched_timings, key=lambda x: x[3])
        min_entry = min(matched_timings, key=lambda x: x[3])
        print("\n=== Timing (oversendt_at → oppfølging_start_dato, matched+sent only) ===")
        print(f"  Candidates with timing data:  {len(matched_timings)}")
        print(f"  Average lag:  {avg_diff:.0f}s  ({avg_diff / 3600:.1f}h)")
        print(f"  Max lag:      {max_entry[3]:.0f}s  ({max_entry[3] / 3600:.1f}h)  aktor_id={max_entry[0]}")
        print(f"    oversendt_at={max_entry[1].isoformat()}  oppfølging_start={max_entry[2].isoformat()}")
        print(f"  Min lag:      {min_entry[3]:.0f}s  ({min_entry[3] / 3600:.1f}h)  aktor_id={min_entry[0]}")
        print(f"    oversendt_at={min_entry[1].isoformat()}  oppfølging_start={min_entry[2].isoformat()}")

    if show("discrepancies"):
        if discrepancies:
            print(f"\n=== ⚠️  Discrepancies — oppfølging_start_dato BEFORE oversendt_at ({len(discrepancies)}) ===")
            for aktor_id, oversendt_at, oppfolging_start, diff_seconds in sorted(discrepancies, key=lambda x: x[3]):
                print(f"  aktor_id={aktor_id}")
                print(f"    oversendt_at:          {oversendt_at.isoformat()}")
                print(f"    oppfølging_start_dato: {oppfolging_start.isoformat()}")
                print(f"    diff: {diff_seconds:.0f}s ({diff_seconds / 3600:.1f}h)")
        else:
            print("\n=== Discrepancies ===")
            print("  ✅ No timing discrepancies — all oversendt_at precede oppfølging_start_dato")


if __name__ == "__main__":
    main()

