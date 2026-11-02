package gg.mcrep.verification;

import java.time.Instant;

/** A single-use code the Player enters on the website to link their account. */
record LinkCode(String code, Instant expiresAt) {}
