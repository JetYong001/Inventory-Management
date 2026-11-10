package com.inventory.management.service;

import com.inventory.management.dto.request.CustomerRequest;
import com.inventory.management.dto.response.CustomerResponse;
import com.inventory.management.entity.Customer;
import com.inventory.management.exception.CustomerNotFoundException;
import com.inventory.management.repository.CustomerRepository;
import com.inventory.management.util.BusinessCodeGenerator;

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

        Customer customer = new Customer();

        customer.setCustomerCode(BusinessCodeGenerator.temporary());
        customer.setCustomerName(request.getCustomerName());
        customer.setPhone(request.getPhone());
        customer.setEmail(request.getEmail());
        customer.setAddress(request.getAddress());
        customer.setActive(true);

        Customer saved = customerRepository.saveAndFlush(customer);

        saved.setCustomerCode(
                BusinessCodeGenerator.format("C", saved.getId())
        );

        return convertToResponse(customerRepository.save(saved));
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
        return convertToResponse(findCustomerById(id));
    }

    public CustomerResponse updateCustomer(
            Long id,
            CustomerRequest request
    ) {
        Customer customer = findCustomerById(id);

        customer.setCustomerName(request.getCustomerName());
        customer.setPhone(request.getPhone());
        customer.setEmail(request.getEmail());
        customer.setAddress(request.getAddress());

        return convertToResponse(customerRepository.save(customer));
    }

    public void deactivateCustomer(Long id) {
        Customer customer = findCustomerById(id);
        customer.setActive(false);
        customerRepository.save(customer);
    }

    public CustomerResponse activateCustomer(Long id) {
        Customer customer = findCustomerById(id);
        customer.setActive(true);

        return convertToResponse(customerRepository.save(customer));
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