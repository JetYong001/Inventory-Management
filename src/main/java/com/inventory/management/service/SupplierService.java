
package com.inventory.management.service;

import com.inventory.management.dto.request.SupplierRequest;
import com.inventory.management.dto.response.SupplierResponse;
import com.inventory.management.entity.Supplier;
import com.inventory.management.exception.DuplicateSupplierException;
import com.inventory.management.exception.SupplierNotFoundException;
import com.inventory.management.repository.SupplierRepository;
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

        if (supplierRepository.existsBySupplierCode(request.getSupplierCode())) {
            throw new DuplicateSupplierException(
                    "Supplier code already exists: " + request.getSupplierCode()
            );
        }

        Supplier supplier = new Supplier();

        supplier.setSupplierCode(request.getSupplierCode());
        supplier.setSupplierName(request.getSupplierName());
        supplier.setContactPerson(request.getContactPerson());
        supplier.setPhone(request.getPhone());
        supplier.setEmail(request.getEmail());
        supplier.setAddress(request.getAddress());
        supplier.setActive(true);

        Supplier savedSupplier = supplierRepository.save(supplier);

        return convertToResponse(savedSupplier);
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

        Supplier supplier = findSupplierById(id);

        return convertToResponse(supplier);
    }

    public SupplierResponse updateSupplier(Long id, SupplierRequest request) {

        Supplier supplier = findSupplierById(id);

        boolean codeExists =
                supplierRepository.existsBySupplierCode(request.getSupplierCode());

        if (codeExists && !supplier.getSupplierCode().equals(request.getSupplierCode())) {
            throw new DuplicateSupplierException(
                    "Supplier code already exists: " + request.getSupplierCode()
            );
        }

        supplier.setSupplierCode(request.getSupplierCode());
        supplier.setSupplierName(request.getSupplierName());
        supplier.setContactPerson(request.getContactPerson());
        supplier.setPhone(request.getPhone());
        supplier.setEmail(request.getEmail());
        supplier.setAddress(request.getAddress());

        Supplier updatedSupplier = supplierRepository.save(supplier);

        return convertToResponse(updatedSupplier);
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
