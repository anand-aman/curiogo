# CurioGo

A simple, learner-friendly **URL shortener** by CurioDesk. Turn long links into short codes, redirect visitors, and track basic click counts.

## Overview

CurioGo takes a long URL and gives back a short, shareable link. When someone opens that link, CurioGo looks up the original address and sends them straight to it. Each short link can carry an optional custom alias and an expiry time, and every visit is counted so you can see how often a link is used.

Short codes are generated from the record's database id using Base62 encoding, which keeps them compact and collision-free. Redirects stay fast because click counts are buffered in memory and written to the database in the background rather than on every request.

## What it does

- Create a short link from a long URL, optionally with a custom alias and an expiry.
- Redirect a short code to its original URL (`302`).
- Return `410 Gone` once a link has expired.
- Count clicks asynchronously without slowing down redirects.
- Validate incoming URLs and reject malformed or unsupported input.

## Tech stack

| Concern   | Choice                          |
| --------- | ------------------------------- |
| Language  | Java 21 (LTS, virtual threads)  |
| Framework | Spring Boot 3.5.13              |
| Database  | PostgreSQL 16                   |
| Migrations| Liquibase                       |
| API docs  | springdoc-openapi (Swagger UI)  |
| Tests     | JUnit 5 + Testcontainers        |
| Build     | Maven (wrapper included)        |
