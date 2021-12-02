package pl.redhat.samples.camel.person.route;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestBindingMode;
import org.springframework.stereotype.Component;
import pl.redhat.samples.camel.person.domain.Person;
import pl.redhat.samples.camel.person.service.PersonService;

@Component
public class PersonRouteBuilder extends RouteBuilder {

    @Override
    public void configure() throws Exception {

        restConfiguration()
                .contextPath("/camel")
                .apiContextPath("/v3/api-docs")
                .apiProperty("api.title", "Person Management API")
                .apiProperty("api.version", "1.0")
                .apiContextRouteId("doc-api")
                .port(8080)
                .bindingMode(RestBindingMode.json);

        rest("/persons")
                .get("/{id}")
                    .route()
                    .bean(PersonService.class, "findById(${header.id})")
                .endRest()
                .get("/")
                    .route()
                    .bean(PersonService.class, "findAll")
                .endRest()
                .get("/older-than/{age}")
                    .route()
                    .bean(PersonService.class, "countOlderThan(${header.age})")
                .endRest()
                .post().consumes("application/json").type(Person.class)
                    .route()
                    .bean(PersonService.class, "add(${body})")
                    .log("New: ${body}")
                .endRest()
                .delete("/{id}")
                    .route()
                    .bean(PersonService.class, "delete(${header.id})")
                .endRest();
    }
}
