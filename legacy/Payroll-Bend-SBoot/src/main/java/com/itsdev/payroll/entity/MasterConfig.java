package com.itsdev.payroll.entity;


import com.fasterxml.jackson.databind.JsonNode;
import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;

@Entity
@Table(name = "masterConfig")
public class MasterConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String componentName;

    @Type(JsonType.class)
    @Column(columnDefinition = "json")
    private JsonNode configData; // Default config stored as JSON


    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getComponentName() { return componentName; }
    public void setComponentName(String componentName) { this.componentName = componentName; }

    public JsonNode getConfigData() { return configData; }
    public void setConfigData(JsonNode configData) { this.configData = configData; }
}
