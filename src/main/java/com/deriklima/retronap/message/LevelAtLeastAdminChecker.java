package com.deriklima.retronap.message;

import com.deriklima.retronap.*;
import com.deriklima.retronap.model.UserLevelTypes;
import com.deriklima.retronap.session.*;

public class LevelAtLeastAdminChecker extends ConditionChecker {

  public boolean checkCondition(MessageContext session, Message message) {
    UserLevelTypes userLevel = session.getUser().getLevel();
    if (!userLevel.equals(UserLevelTypes.ELITE) && !userLevel.equals(UserLevelTypes.ADMIN)) {
      String errorMsg = "User must be Admin level or above to execute that command!";
      setErrorMessage(new Message(MessageTypes.SERVER_GENERAL_ERROR, errorMsg));
      return false;
    }
    return true;
  }
}
