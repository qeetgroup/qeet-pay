package com.qeetgroup.qeetpay.itc;

/**
 * One line of supplier-filed GSTR-2B data pulled from the GST portal: the total GST a supplier
 * reported for an invoice. Matched against the merchant's purchase invoices during reconciliation.
 */
public record Gstr2bLine(String supplierGstin, String invoiceNumber, long totalGstMinor) {}
