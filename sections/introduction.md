# Introduction

This document is a module as part of the [[[ADR]]].
It is based on [[[?DK-GB]]] which was initially written for ebMS2 and WUS-based exchanges in Digikoppeling.

Resources in REST APIs are usually represented as a JSON or XML body.
Some payloads are not suitable for such a format, such as media files or bulk data exports.
This module provides rules for describing such files as a [=metadata resource=] and transferring them reliably using two patterns: pull and push.

## Pull pattern

The sender (server) makes the file available at a dedicated URI (`contentUri`). The receiver (client) fetches the file with `GET`. Interrupted transfer can be resumed.

## Push pattern

The sender (client) requests an upload URI from the receiver (server), using `POST`. Subsequently, the sender uploads the file to the provided URI (`contentUri`), using `PUT`.
