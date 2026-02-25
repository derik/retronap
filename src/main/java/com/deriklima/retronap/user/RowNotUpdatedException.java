package com.deriklima.retronap.user;

class RowNotUpdatedException extends RuntimeException {
  RowNotUpdatedException(String message) {
    super(message);
  }
}
