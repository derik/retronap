package com.deriklima.retronap.user;

import com.deriklima.retronap.message.Message;
import com.deriklima.retronap.message.MessageContext;
import com.deriklima.retronap.message.MessageHandler;
import com.deriklima.retronap.message.MessageTypes;
import com.deriklima.retronap.model.UserInfo;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ClientInfoRegistrationHandler extends MessageHandler {
  private final UserPersistenceStore userPersistenceStore;

  @Override
  protected void processMessage(Message m, MessageContext session) {
    MessageContext userState = session;
    String[] data = m.getDataString().split(": ");
    UserInfo userInfo = new UserInfo();
    userInfo.setZipCode(data[1].split("\t")[0]);
    userInfo.setCountry(data[2].split("\t")[0]);
    userInfo.setGender(data[3].split("\t")[0]);
    userInfo.setAge(data[4].split("\t")[0]);
    userInfo.setIncome(data[5].split("\t")[0]);
    userInfo.setEducation(data[6].split("\t")[0]);

    userPersistenceStore.saveUserInfo(userInfo, userState.getNickname());
  }

  @Override
  public List<Integer> getHandledMessageTypes() {
    return List.of(MessageTypes.CLIENT_REGISTRATION_INFO);
  }
}
