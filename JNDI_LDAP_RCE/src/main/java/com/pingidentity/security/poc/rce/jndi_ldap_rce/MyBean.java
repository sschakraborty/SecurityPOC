package com.pingidentity.security.poc.rce.jndi_ldap_rce;

public class MyBean {
    private String name;
    private Object body;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Object getBody() {
        return body;
    }

    public void setBody(Object body) {
        this.body = body;
    }

    @Override
    public String toString() {
        return "MyBean {" +
            "name='" + name + '\'' +
            ", body=" + body +
            '}';
    }
}
