package com.deriklima.retronap.hotlist;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@IdClass(HotlistEntry.HotlistEntryId.class)
public class HotlistEntry {

  @Id private UUID targetUserId;

  @Id private UUID stalkerUserId;

  public static class HotlistEntryId implements Serializable {
    private UUID targetUserId;
    private UUID stalkerUserId;
  }
}
