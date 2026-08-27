package com.deriklima.retronap.message;

public abstract class ConditionChecker {
  private Message errorMessage;

  // checkCondition will verify that the ConditionCheckers pre- or post- condition is true
  public abstract boolean checkCondition(MessageContext session, Message message);

  /* getErrorMessage will return the error message created by the ConditionChecker.  This method
  should only be called if checkCondition is false */
  public Message getErrorMessage() {
    return errorMessage;
  }

  protected void setErrorMessage(Message errorMessage) {
    this.errorMessage = errorMessage;
  }
}
