// Tests for the migration table lister.
//
//   node --test infra/ci/
//
// The lister is what two CI gates ask "which tables does this migration create, and does
// each declare tenant_id". A gate is only as good as the parser under it, and #137 was
// exactly that failure: the check looked right and answered a different question. Every
// case below is one that defeated an earlier version of it.

import { test } from "node:test";
import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { mkdtempSync, writeFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { fileURLToPath } from "node:url";

const LISTER = fileURLToPath(new URL("./list-created-tables.mjs", import.meta.url));

/** Runs the lister over one throwaway .sql file and returns "table\tyes|no" rows. */
function list(sql) {
  const dir = mkdtempSync(join(tmpdir(), "lister-"));
  const file = join(dir, "V001__case.sql");
  try {
    writeFileSync(file, sql);
    const out = execFileSync(process.execPath, [LISTER, file], { encoding: "utf8" });
    return out
      .split("\n")
      .filter(Boolean)
      .map((line) => line.split("\t").slice(1).join("\t"));
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
}

function listExpectingFailure(sql) {
  const dir = mkdtempSync(join(tmpdir(), "lister-"));
  const file = join(dir, "V001__case.sql");
  try {
    writeFileSync(file, sql);
    execFileSync(process.execPath, [LISTER, file], { encoding: "utf8", stdio: "pipe" });
    return null;
  } catch (e) {
    return e;
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
}

test("reports each table separately, not one verdict per file", () => {
  assert.deepEqual(
    list("CREATE TABLE core.a (tenant_id uuid);\nCREATE TABLE core.b (id int);\n"),
    ["core.a\tyes", "core.b\tno"],
  );
});

test("an unbalanced paren inside a string literal does not swallow the rest of the file", () => {
  assert.deepEqual(
    list("CREATE TABLE core.a (\n label text DEFAULT '(',\n tenant_id uuid\n);\nCREATE TABLE core.b (\n id int\n);\n"),
    ["core.a\tyes", "core.b\tno"],
  );
});

test("two statements on one line are both reported", () => {
  assert.deepEqual(
    list("CREATE TABLE core.a (tenant_id uuid); CREATE TABLE core.b (id int);\n"),
    ["core.a\tyes", "core.b\tno"],
  );
});

test("a line comment naming a table is not a table", () => {
  assert.deepEqual(
    list("-- create table core.ghost\nCREATE TABLE core.real (\n tenant_id uuid\n);\n"),
    ["core.real\tyes"],
  );
});

test("a block comment naming a table is not a table, and block comments nest", () => {
  assert.deepEqual(
    list("/* create table core.ghost /* inner */ still comment */\nCREATE TABLE core.real (tenant_id uuid);\n"),
    ["core.real\tyes"],
  );
});

test("tenant_id in a comment does not satisfy the gate", () => {
  assert.deepEqual(list("CREATE TABLE core.b (\n id int -- no tenant_id here\n);\n"), ["core.b\tno"]);
});

test("tenant_id as part of a longer column name does not satisfy the gate", () => {
  assert.deepEqual(list("CREATE TABLE core.b (old_tenant_identifier uuid);\n"), ["core.b\tno"]);
});

test("a paren inside a CHECK constraint's string literal is not counted", () => {
  assert.deepEqual(
    list("CREATE TABLE IF NOT EXISTS core.c (\n tenant_id uuid,\n kind text CHECK (kind IN ('A',')','B'))\n);\n"),
    ["core.c\tyes"],
  );
});

test("a CREATE TABLE inside a function body is not a migration's table", () => {
  assert.deepEqual(
    list(
      "CREATE TABLE core.real (tenant_id uuid);\n" +
        "CREATE FUNCTION f() RETURNS void LANGUAGE plpgsql AS $$\nBEGIN\n" +
        "  CREATE TABLE core.temp_inside (id int);\nEND;\n$$;\n",
    ),
    ["core.real\tyes"],
  );
});

test("case and whitespace do not matter — SQL keywords are case-insensitive", () => {
  assert.deepEqual(list("create   table\n  Core.Mixed\n  (Tenant_Id uuid);\n"), ["core.mixed\tyes"]);
});

test("an escaped quote inside a literal does not end it", () => {
  assert.deepEqual(
    list("CREATE TABLE core.a (\n note text DEFAULT 'it''s (fine',\n tenant_id uuid\n);\n"),
    ["core.a\tyes"],
  );
});

test("a quoted identifier keeps its name and is not mistaken for a literal", () => {
  assert.deepEqual(list('CREATE TABLE core."order" (tenant_id uuid);\n'), ["core.order\tyes"]);
});

test("an unparseable file fails loudly rather than reporting no tables", () => {
  // Silence must mean "no tables here", never "I lost my place".
  const failure = listExpectingFailure("CREATE TABLE core.a (\n note text DEFAULT 'unterminated\n);\n");
  assert.notEqual(failure, null, "expected a non-zero exit");
  assert.match(String(failure.stderr), /unterminated string literal/);
});

test("an unclosed column list fails loudly", () => {
  const failure = listExpectingFailure("CREATE TABLE core.a (\n tenant_id uuid\n");
  assert.notEqual(failure, null, "expected a non-zero exit");
  assert.match(String(failure.stderr), /unclosed column list/);
});
