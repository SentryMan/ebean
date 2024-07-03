package org.tests.basic.type;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Transient;
import java.util.List;
import java.util.Map;

@Entity
public class BSimpleWithGen {

  @Id
  private Integer id;

  private String name;

  private String desc;

  @Transient
  private Map<String, List<String>> someMap;

  public BSimpleWithGen(String name) {
    this.name = name;
  }

  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Map<String, List<String>> getSomeMap() {
    return someMap;
  }

  public void setSomeMap(Map<String, List<String>> someMap) {
    this.someMap = someMap;
  }

  public String getDesc() {
    return desc;
  }

  public void setDesc(String desc) {
    this.desc = desc;
  }
}
