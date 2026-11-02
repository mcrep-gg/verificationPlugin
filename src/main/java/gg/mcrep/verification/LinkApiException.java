package gg.mcrep.verification;

/** The website did not issue a Link Code. The message is for the server console, not for Players. */
final class LinkApiException extends RuntimeException {
  enum Reason {
    /** The website rejected our secret: config.yml and LINK_SERVER_SECRET differ. */
    UNAUTHORIZED,
    /** The website has no LINK_SERVER_SECRET set. */
    NOT_CONFIGURED,
    /** Anything else: the website is down, slow, or answered with something unexpected. */
    FAILED,
  }

  private final Reason reason;

  LinkApiException(Reason reason, String message) {
    super(message);
    this.reason = reason;
  }

  Reason reason() {
    return reason;
  }
}
