package org.tests.model.basic;

import io.ebean.annotation.Cache;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.Table;

/**
 * Cached entity for inheritance.
 *
 * @author Roland Praml, FOCONIS AG
 */
@Entity
@Table(name = "o_cached_inherit")
@Cache
public final class OCachedInhRoot {

  @Id
  Long id;

  String name;

  String childAData;
  String childBData;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }


  public void setChildAData(String childData) {
    this.childAData = childData;
  }

  public String getChildAData() {
    return childAData;
  }
}
