#!/usr/bin/env node
// list-created-tables.mjs — one record per CREATE TABLE statement, not one per file.
//
//   node infra/ci/list-created-tables.mjs <file.sql>...
//
// Output, tab-separated, one line per statement:
//
//   <file>\t<schema.table>\t<yes|no the column list declares tenant_id>
//
// Exit 1, with the reason on stderr, if a file cannot be parsed. A parser that loses its
// place must say so: silence has to mean "no tables here", never "I gave up".
//
// ── why this exists (#137)
//
// The migration gates in .github/workflows/ci.yml used `grep -l` / `grep -L`, which answer
// a question about a *file*. A migration creating two tables and giving only the first a
// tenant_id, an RLS switch and an isolation policy satisfied every one of them — the file
// contains tenant_id, the file contains ENABLE ROW LEVEL SECURITY, the file contains
// CREATE POLICY tenant_isolation — and the unprotected second table shipped.
//
// ── why it is not a regex, and not awk
//
// The first fix for #137 was an awk pass that tracked paren depth line by line. Review
// found three legal inputs that defeated it, each of which made a table disappear from the
// output entirely — and a table nothing lists is a table no gate judges:
//
//   1. `label text DEFAULT '('` — an unbalanced paren inside a string literal left the
//      depth counter permanently positive, so every table in the file was dropped.
//   2. `-- create table core.ghost` — a comment was read as a statement, so the gate
//      reported a table that does not exist and missed the real one that followed.
//   3. `CREATE TABLE a (...); CREATE TABLE b (...);` on one line — only the first was seen.
//
// All three come from the same mistake: pattern-matching text that has comments and string
// literals in it. So this reads the file as a character stream first and removes what is
// not code — line comments, block comments, string literals, dollar-quoted bodies — while
// keeping quoted identifiers, which are part of a table's name. Only then does it look for
// statements. Parens inside a literal cannot be counted because by then they are gone.

import { readFileSync } from "node:fs";

/**
 * Blanks out everything that is not SQL code, preserving offsets and newlines so that a
 * reported position still matches the original file.
 *
 * Kept: double-quoted identifiers, because "order" is a legal table name.
 * Removed: -- line comments, C-style block comments, 'string literals', $tag$ bodies.
 */
function stripNonCode(sql, file) {
  const out = Array.from(sql);
  let i = 0;
  const blank = (from, to) => {
    for (let k = from; k < to && k < out.length; k++) {
      if (out[k] !== "\n") out[k] = " ";
    }
  };

  while (i < sql.length) {
    const two = sql.slice(i, i + 2);

    if (two === "--") {
      const end = sql.indexOf("\n", i);
      blank(i, end === -1 ? sql.length : end);
      i = end === -1 ? sql.length : end;
      continue;
    }

    if (two === "/*") {
      // Postgres block comments nest, so a depth counter is required, not indexOf("*/").
      let depth = 1;
      let j = i + 2;
      while (j < sql.length && depth > 0) {
        if (sql.slice(j, j + 2) === "/*") {
          depth++;
          j += 2;
        } else if (sql.slice(j, j + 2) === "*/") {
          depth--;
          j += 2;
        } else {
          j++;
        }
      }
      if (depth > 0) throw new Error(`${file}: unterminated block comment`);
      blank(i, j);
      i = j;
      continue;
    }

    if (sql[i] === "'") {
      // '' inside a literal is an escaped quote, not the end of it.
      let j = i + 1;
      for (;;) {
        const q = sql.indexOf("'", j);
        if (q === -1) throw new Error(`${file}: unterminated string literal`);
        if (sql[q + 1] === "'") {
          j = q + 2;
          continue;
        }
        j = q + 1;
        break;
      }
      blank(i, j);
      i = j;
      continue;
    }

    if (sql[i] === "$") {
      // $$ ... $$ or $tag$ ... $tag$ — a function body, which may contain anything.
      const tag = /^\$[A-Za-z_][A-Za-z0-9_]*\$|^\$\$/.exec(sql.slice(i));
      if (tag) {
        const close = sql.indexOf(tag[0], i + tag[0].length);
        if (close === -1) throw new Error(`${file}: unterminated dollar-quoted block`);
        const end = close + tag[0].length;
        blank(i, end);
        i = end;
        continue;
      }
    }

    if (sql[i] === '"') {
      // A quoted identifier is part of the name. Skip over it without blanking it, so
      // that a quote containing a paren or a semicolon cannot be miscounted either.
      const close = sql.indexOf('"', i + 1);
      if (close === -1) throw new Error(`${file}: unterminated quoted identifier`);
      i = close + 1;
      continue;
    }

    i++;
  }

  return out.join("");
}

const CREATE_TABLE = /create\s+table\s+(?:if\s+not\s+exists\s+)?("?[\w$]+"?(?:\s*\.\s*"?[\w$]+"?)?)/giu;

/** Every CREATE TABLE in one file, with whether its own column list declares tenant_id. */
function tablesIn(file) {
  const code = stripNonCode(readFileSync(file, "utf8"), file);
  const found = [];

  for (const match of code.matchAll(CREATE_TABLE)) {
    const name = match[1].replace(/["\s]/g, "").toLowerCase();

    // The column list runs from the first ( after the name to its matching ). Every paren
    // between them is real code, because literals and comments were removed above.
    const open = code.indexOf("(", match.index + match[0].length);
    if (open === -1) {
      throw new Error(`${file}: CREATE TABLE ${name} has no column list`);
    }
    let depth = 0;
    let close = -1;
    for (let k = open; k < code.length; k++) {
      if (code[k] === "(") depth++;
      else if (code[k] === ")" && --depth === 0) {
        close = k;
        break;
      }
    }
    if (close === -1) {
      throw new Error(`${file}: CREATE TABLE ${name} has an unclosed column list`);
    }

    // \b so that a column named `old_tenant_identifier` does not satisfy the gate, and so
    // that the word in a comment cannot either - comments are already gone by here.
    const declaresTenantId = /\btenant_id\b/i.test(code.slice(open, close + 1));
    found.push({ name, declaresTenantId });
  }

  return found;
}

const files = process.argv.slice(2);
let failed = false;

for (const file of files) {
  try {
    for (const { name, declaresTenantId } of tablesIn(file)) {
      process.stdout.write(`${file}\t${name}\t${declaresTenantId ? "yes" : "no"}\n`);
    }
  } catch (e) {
    process.stderr.write(`${e.message}\n`);
    failed = true;
  }
}

process.exit(failed ? 1 : 0);
