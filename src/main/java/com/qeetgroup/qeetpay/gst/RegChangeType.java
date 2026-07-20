package com.qeetgroup.qeetpay.gst;

/**
 * The kind of announced GST change tracked by the Impact Radar (PRD Module 06.5). Only a per-HSN/SAC
 * rate change is modelled today; the enum leaves room for exemption/cess changes without a schema move.
 */
public enum RegChangeType {
    RATE_CHANGE
}
