
package com.inventory.management.service;

import com.inventory.management.dto.request.CustomerRequest;
import com.inventory.management.dto.response.CustomerResponse;
import com.inventory.management.entity.Customer;
import com.inventory.management.exception.CustomerNotFoundException;
import com.inventory.management.exception.DuplicateCustomerException;
import com.inventory.management.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public CustomerResponse createCustomer(CustomerRequest request) {

        if (customerRepository.existsByCustomerCode(request.getCustomerCode())) {
            throw new DuplicateCustomerException(
                    "Customer code already exists: " + request.getCustomerCode()
            );
        }

        Customer customer = new Customer();

        customer.setCustomerCode(request.getCustomerCode());
        customer.setCustomerName(request.getCustomerName());
        customer.setPhone(request.getPhone());
        customer.setEmail(request.getEmail());
        customer.setAddress(request.getAddress());
        customer.setActive(true);

        Customer savedCustomer = customerRepository.save(customer);

        return convertToResponse(savedCustomer);
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> getAllCustomers() {

        return customerRepository.findAll()
                .stream()
                .map(this::convertToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CustomerResponse getCustomerById(Long id) {

        Customer customer = findCustomerById(id);

        return convertToResponse(customer);
    }

    public CustomerResponse updateCustomer(Long id, CustomerRequest request) {

        Customer customer = findCustomerById(id);

        boolean codeExists =
                customerRepository.existsByCustomerCode(request.getCustomerCode());

        if (codeExists &&
                !customer.getCustomerCode().equals(request.getCustomerCode())) {

            throw new DuplicateCustomerException(
                    "Customer code already exists: " + request.getCustomerCode()
            );
        }

        customer.setCustomerCode(request.getCustomerCode());
        customer.setCustomerName(request.getCustomerName());
        customer.setPhone(request.getPhone());
        customer.setEmail(request.getEmail());
        customer.setAddress(request.getAddress());

        Customer updatedCustomer = customerRepository.save(customer);

        return convertToResponse(updatedCustomer);
    }

    public void deactivateCustomer(Long id) {

        Customer customer = findCustomerById(id);

        customer.setActive(false);

        customerRepository.save(customer);
    }

    public CustomerResponse activateCustomer(Long id) {

        Customer customer = findCustomerById(id);

        customer.setActive(true);

        Customer updatedCustomer = customerRepository.save(customer);

        return convertToResponse(updatedCustomer);
    }

    private Customer findCustomerById(Long id) {

        return customerRepository.findById(id)
                .orElseThrow(() -> new CustomerNotFoundException(
                        "Customer not found with ID: " + id
                ));
    }

    private CustomerResponse convertToResponse(Customer customer) {

        CustomerResponse response = new CustomerResponse();

        response.setId(customer.getId());
        response.setCustomerCode(customer.getCustomerCode());
        response.setCustomerName(customer.getCustomerName());
        response.setPhone(customer.getPhone());
        response.setEmail(customer.getEmail());
        response.setAddress(customer.getAddress());
        response.setActive(customer.getActive());
        response.setCreatedAt(customer.getCreatedAt());

        return response;
    }
}
