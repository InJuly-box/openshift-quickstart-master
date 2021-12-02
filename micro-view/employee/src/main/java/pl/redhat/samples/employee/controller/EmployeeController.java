package pl.redhat.samples.employee.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import pl.redhat.samples.employee.model.Employee;
import pl.redhat.samples.employee.repository.EmployeeRepository;

import java.util.List;

@RestController
public class EmployeeController {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmployeeController.class);

    @Autowired
    EmployeeRepository repository;

    @PostMapping("/")
    public Employee add(@RequestBody Employee employee) {
        LOGGER.info("Employee add...: {}", employee);
        return repository.save(employee);
    }

    @GetMapping("/{id}")
    public Employee findById(@PathVariable("id") String id) {
        LOGGER.info("Employee find: id={}", id);
        return repository.findById(id).get();
    }

    @GetMapping("/")
    public Iterable<Employee> findAll() {
        LOGGER.info("Employee find");
        return repository.findAll();
    }

    @GetMapping("/department/{departmentId}")
    public List<Employee> findByDepartment(@PathVariable("departmentId") String departmentId) {
        LOGGER.info("Employee find: departmentId={}", departmentId);
        return repository.findByDepartmentId(departmentId);
    }

    @GetMapping("/organization/{organizationId}")
    public List<Employee> findByOrganization(@PathVariable("organizationId") String organizationId) {
        LOGGER.info("Employee find: organizationId={}", organizationId);
        return repository.findByOrganizationId(organizationId);
    }

    @GetMapping("/department-with-delay/{departmentId}")
    public List<Employee> findByDepartmentWithDelay(@PathVariable("departmentId") String departmentId) throws InterruptedException {
        LOGGER.info("Employee find: departmentId={}", departmentId);
        Thread.sleep(2000);
        return repository.findByDepartmentId(departmentId);
    }

}
