# Security Policy

## Reporting a Vulnerability

If you discover a security vulnerability, please report it responsibly:

1. Do **not** open a public GitHub issue.
2. Email the maintainers with a description and reproduction steps.
3. Allow up to 72 hours for an initial response.

## Supported Versions

Only the latest release line receives security fixes.

## Hardening Checklist for Deployments

- Change the default `admin` password immediately after first boot.
- Set a strong `JWT_SECRET` (at least 256 bits) via environment variable.
- Put the platform behind TLS (reverse proxy in front of Docker Compose).
- Restrict `CORS_ALLOWED_ORIGINS` to your actual frontend origin.
- Store SSH credentials encrypted; rotate the `CREDENTIALS_MASTER_KEY` regularly.
- Enable the audit log hash-chain integrity check on a schedule.
