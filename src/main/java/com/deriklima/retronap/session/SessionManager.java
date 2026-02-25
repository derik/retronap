package com.deriklima.retronap.session;

import com.deriklima.retronap.message.MessageContext;
import com.deriklima.retronap.model.ResumeParameters;
import com.deriklima.retronap.model.SearchParameters;
import com.deriklima.retronap.model.SearchResult;
import com.deriklima.retronap.model.SharedFile;
import com.deriklima.retronap.search.ShareProvider;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class SessionManager implements ShareProvider {

  private final List<Session> activeSessions = new CopyOnWriteArrayList<>();

  @EventListener
  public void handleSessionEvent(SessionEvent se) {
    switch (se.getType()) {
      case SessionEvent.REGISTER -> addActiveSession((Session) se.getSession());
      case SessionEvent.TERMINATE -> removeActiveSession((Session) se.getSession());
    }
  }

  public void addActiveSession(Session s) {
    activeSessions.add(s);
  }

  public void removeActiveSession(Session s) {
    activeSessions.remove(s);
  }

  public MessageContext searchForSession(String nickname) {
    return activeSessions.stream()
        .filter(s -> s != null && nickname.equals(s.getNickname()))
        .findFirst()
        .orElse(null);
  }

  public UserState searchForUserState(String nickname) {
    Session session = (Session) searchForSession(nickname);
    return (session != null) ? session.getUserState() : null;
  }

  public List<SearchResult> searchForShares(SearchParameters params) {
    return activeSessions.stream()
        .flatMap(
            s -> {
              List<SharedFile> results = s.search(params);
              if (results == null) {
                return Stream.empty();
              }
              return results.stream()
                  .map(
                      share ->
                          new SearchResult(
                              share,
                              s.getNickname(),
                              s.getIPAddress(),
                              s.getUser().getLinkSpeedValue()));
            })
        .limit(params.getMaxResults())
        .collect(Collectors.toList());
  }

  public List<SearchResult> searchForShares(ResumeParameters params) {
    return activeSessions.stream()
        .flatMap(
            s -> {
              List<SharedFile> results = s.search(params);
              if (results == null) {
                return Stream.empty();
              }
              return results.stream()
                  .map(
                      share -> {
                        SearchResult result =
                            new SearchResult(
                                share,
                                s.getNickname(),
                                s.getIPAddress(),
                                s.getUser().getLinkSpeedValue());
                        result.setPort(s.getUser().getDataPort());
                        return result;
                      });
            })
        .limit(params.getMaxResults())
        .collect(Collectors.toList());
  }

  public Collection<MessageContext> getSessions() {
    return activeSessions.stream().map(s -> (MessageContext) s).toList();
  }
}
