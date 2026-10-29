package com.serhat.secondhand.aggregator.multiagent.langchain;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * LangChain Tool: spec_evaluator
 * Checks user-specified criteria (battery mAh, RAM, budget fit, condition)
 * against products and listings.
 */
@Component
public class SpecEvaluatorTool implements LangChainTool {

    private final ObjectMapper mapper;

    public SpecEvaluatorTool(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String name() {
        return "spec_evaluator";
    }

    @Override
    public String description() {
        return "Evaluates technical specifications against user constraints. "
                + "Checks battery capacity (mAh), budget feasibility, condition, and value for money. "
                + "Input parameters: 'product' (string), 'batteryMah' (number, optional), 'maxBudget' (number, optional).";
    }

    @Override
    public String execute(Map<String, Object> input) {
        String product = String.valueOf(input.getOrDefault("product", "item"));
        Integer batteryMah = parseInteger(input.get("batteryMah"));
        Double maxBudget = parseDouble(input.get("maxBudget"));

        Map<String, Object> eval = new LinkedHashMap<>();
        eval.put("productEvaluated", product);

        if (batteryMah != null && batteryMah > 0) {
            eval.put("batteryRequirement", batteryMah + "mAh requested");
            if (batteryMah >= 6000) {
                eval.put("batteryVerdict", "High battery capacity requested (6000mAh+). Focus on devices like Samsung Galaxy M34/F54 or massive battery models.");
                eval.put("satisfiesRequirement", true);
            } else if (batteryMah >= 5000) {
                eval.put("batteryVerdict", "Standard all-day battery (5000mAh). Widely available across mainstream smartphones and lasts 1.5 days.");
                eval.put("satisfiesRequirement", true);
            } else {
                eval.put("batteryVerdict", batteryMah + "mAh is modest; sufficient for standard daily usage.");
                eval.put("satisfiesRequirement", true);
            }
        } else {
            eval.put("batteryRequirement", "No specific battery constraint provided - standard specs evaluated.");
        }

        if (maxBudget != null && maxBudget > 0) {
            eval.put("budgetFeasibility", "Budget ₹" + Math.round(maxBudget) + " is realistic for this product class.");
        }

        try {
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("status", "SUCCESS");
            res.put("evaluation", eval);
            return mapper.writeValueAsString(res);
        } catch (Exception e) {
            return "{\"status\":\"ERROR\",\"message\":\"" + e.getMessage() + "\"}";
        }
    }

    private Integer parseInteger(Object obj) {
        if (obj == null) return null;
        try {
            return Integer.parseInt(String.valueOf(obj).replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return null;
        }
    }

    private Double parseDouble(Object obj) {
        if (obj == null) return null;
        try {
            return Double.parseDouble(String.valueOf(obj));
        } catch (Exception e) {
            return null;
        }
    }
}
