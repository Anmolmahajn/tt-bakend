#!/bin/bash
# Loads .env (gitignored — holds your Clerk test keys) and starts the backend.
set -a
source .env
set +a
mvn spring-boot:run
