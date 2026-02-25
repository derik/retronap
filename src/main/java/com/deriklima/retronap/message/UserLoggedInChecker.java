package com.deriklima.retronap.message;

import com.deriklima.retronap.session.*;

public class UserLoggedInChecker extends ConditionChecker {

  public boolean checkCondition(MessageContext session, Message message) {
    if (!session.isLoggedIn()) {
      String errorMsg = "User tried sending privileged message while not logged in";
      setErrorMessage(new Message(MessageTypes.SERVER_ERROR, errorMsg));
      return false;
    }
    return true;
  }
}
