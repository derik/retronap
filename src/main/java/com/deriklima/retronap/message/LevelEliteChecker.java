package com.deriklima.retronap.message;

import com.deriklima.retronap.*;
import com.deriklima.retronap.model.UserLevelTypes;
import com.deriklima.retronap.session.*;

public class LevelEliteChecker extends ConditionChecker {

  public boolean checkCondition(MessageContext session, Message message) {
    UserLevelTypes userLevel = session.getUser().getLevel();
    if (!userLevel.equals(UserLevelTypes.ELITE)) {
      String errorMsg = "User must be Elite level to execute that command!";
      setErrorMessage(new Message(MessageTypes.SERVER_GENERAL_ERROR, errorMsg));
      return false;
    }
    return true;
  }
}
