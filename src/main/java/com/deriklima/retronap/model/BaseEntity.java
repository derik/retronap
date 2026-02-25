package com.deriklima.retronap.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Setter
@Getter
public abstract class BaseEntity {

  @GeneratedValue(strategy = GenerationType.AUTO)
  @Column(columnDefinition = "uuid", updatable = false, nullable = false)
  @Id
  private UUID id;

  @CreatedDate private LocalDateTime created;
  @LastModifiedDate private LocalDateTime lastModified;
  @Version private long version;

  protected BaseEntity() {}

  protected BaseEntity(
      UUID id, LocalDateTime created, LocalDateTime lastModified, UUID createdBy, long version) {
    this.id = id;
    this.created = created;
    this.lastModified = lastModified;
    this.version = version;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;

    BaseEntity that = (BaseEntity) o;
    return id.equals(that.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }
}
