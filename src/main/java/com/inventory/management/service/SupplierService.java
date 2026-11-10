package com.inventory.management.service;

import com.inventory.management.dto.request.SupplierRequest;
import com.inventory.management.dto.response.SupplierResponse;
import com.inventory.management.entity.Supplier;
import com.inventory.management.exception.SupplierNotFoundException;
import com.inventory.management.repository.SupplierRepository;
import com.inventory.management.util.BusinessCodeGenerator;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class SupplierService {

    private final SupplierRepository supplierRepository;

    public SupplierService(SupplierRepository supplierRepository) {
        this.supplierRepository = supplierRepository;
    }

    public SupplierResponse createSupplier(SupplierRequest request) {

        Supplier supplier = new Supplier();

        supplier.setSupplierCode(BusinessCodeGenerator.temporary());
        supplier.setSupplierName(request.getSupplierName());
        supplier.setContactPerson(request.getContactPerson());
        supplier.setPhone(request.getPhone());
        supplier.setEmail(request.getEmail());
        supplier.setAddress(request.getAddress());
        supplier.setActive(true);

        Supplier saved = supplierRepository.saveAndFlush(supplier);

        saved.setSupplierCode(
                BusinessCodeGenerator.format("SUP", saved.getId())
        );

        return convertToResponse(supplierRepository.save(saved));
    }

    @Transactional(readOnly = true)
    public List<SupplierResponse> getAllSuppliers() {
        return supplierRepository.findAll()
                .stream()
                .map(this::convertToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SupplierResponse getSupplierById(Long id) {
        return convertToResponse(findSupplierById(id));
    }

    public SupplierResponse updateSupplier(
            Long id,
            SupplierRequest request
    ) {
        Supplier supplier = findSupplierById(id);

        supplier.setSupplierName(request.getSupplierName());
        supplier.setContactPerson(request.getContactPerson());
        supplier.setPhone(request.getPhone());
        supplier.setEmail(request.getEmail());
        supplier.setAddress(request.getAddress());

        return convertToResponse(supplierRepository.save(supplier));
    }

    public void deleteSupplier(Long id) {
        Supplier supplier = findSupplierById(id);
        supplier.setActive(false);
        supplierRepository.save(supplier);
    }

    private Supplier findSupplierById(Long id) {
        return supplierRepository.findById(id)
                .orElseThrow(() -> new SupplierNotFoundException(
                        "Supplier not found with ID: " + id
                ));
    }

    private SupplierResponse convertToResponse(Supplier supplier) {
        SupplierResponse response = new SupplierResponse();

        response.setId(supplier.getId());
        response.setSupplierCode(supplier.getSupplierCode());
        response.setSupplierName(supplier.getSupplierName());
        response.setContactPerson(supplier.getContactPerson());
        response.setPhone(supplier.getPhone());
        response.setEmail(supplier.getEmail());
        response.setAddress(supplier.getAddress());
        response.setActive(supplier.getActive());
        response.setCreatedAt(supplier.getCreatedAt());

        return response;
    }
}