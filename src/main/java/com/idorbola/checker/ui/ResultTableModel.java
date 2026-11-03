package com.idorbola.checker.ui;

import com.idorbola.checker.model.ScanResult;
import com.idorbola.checker.model.Verdict;
import com.idorbola.checker.util.HttpText;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

public class ResultTableModel extends AbstractTableModel {

    private static final String[] BASE_COLS = {"#", "Method", "URL", "Group", "P1 Status", "P2 Status", "Verdict", "Similarity"};

    private final List<ScanResult> allResults = new ArrayList<>();
    private final List<ScanResult> filteredResults = new ArrayList<>();

    private String p1Id = null;
    private String p2Id = null;
    private Verdict filterVerdict = null;
    private String searchQuery = "";
    private String methodFilter = null;
    private String p1StatusFilter = null;
    private String p2StatusFilter = null;

    public static final String ANY = "Any";
    public static final String NO_RESPONSE = "No response";
    public static final String[] STATUS_CLASSES = {ANY, "2xx", "3xx", "4xx", "5xx", NO_RESPONSE};

    private int threshold = 80;

    public void setThreshold(int threshold) { this.threshold = threshold; }

    @Override public int getRowCount()    { return filteredResults.size(); }
    @Override public int getColumnCount() { return BASE_COLS.length; }
    @Override public String getColumnName(int col) { return BASE_COLS[col]; }

    @Override
    public Class<?> getColumnClass(int col) {
        return switch (col) {
            case 0 -> Integer.class;
            case 3, 4, 5 -> Integer.class;
            case 6 -> Verdict.class;
            case 7 -> Float.class;
            default -> String.class;
        };
    }

    @Override
    public Object getValueAt(int row, int col) {
        if (row >= filteredResults.size()) return null;
        ScanResult r = filteredResults.get(row);
        return switch (col) {
            case 0 -> r.getRowIndex() + 1;
            case 1 -> r.getGroup().getRepresentative().method();
            case 2 -> HttpText.displayPath(r.getGroup().getRepresentative().path());
            case 3 -> r.getGroup().size() > 1 ? r.getGroup().size() : null;
            case 4 -> p1Id != null ? r.getStatus(p1Id) : -1;
            case 5 -> p2Id != null ? r.getStatus(p2Id) : -1;
            case 6 -> (p1Id != null && p2Id != null)
                    ? r.getVerdict(p1Id, p2Id, threshold)
                    : Verdict.PENDING;
            case 7 -> (p1Id != null && p2Id != null)
                    ? r.getSimilarity(p1Id, p2Id)
                    : null;
            default -> null;
        };
    }

    public ScanResult getResultAt(int row) {
        if (row < 0 || row >= filteredResults.size()) return null;
        return filteredResults.get(row);
    }

    public void addResult(ScanResult result) {
        int pos = 0;
        while (pos < allResults.size() && allResults.get(pos).getRowIndex() < result.getRowIndex()) pos++;
        allResults.add(pos, result);
        applyFilter();
    }

    public void setResults(List<ScanResult> results) {
        allResults.clear();
        allResults.addAll(results);
        applyFilter();
    }

    public void clearResults() {
        allResults.clear();
        filteredResults.clear();
        fireTableDataChanged();
    }

    public void setProfilePair(String p1Id, String p2Id) {
        this.p1Id = p1Id;
        this.p2Id = p2Id;
        applyFilter();
    }

    public void setFilter(Verdict filter) {
        this.filterVerdict = filter;
        applyFilter();
    }

    public void setSearch(String query) {
        this.searchQuery = query == null ? "" : query.toLowerCase().trim();
        applyFilter();
    }

    /** null or {@link #ANY} shows every method. */
    public void setMethodFilter(String method) {
        this.methodFilter = method == null || ANY.equals(method) ? null : method;
        applyFilter();
    }

    /** Values from {@link #STATUS_CLASSES}; null or {@link #ANY} disables the filter. */
    public void setStatusFilters(String p1Status, String p2Status) {
        this.p1StatusFilter = p1Status == null || ANY.equals(p1Status) ? null : p1Status;
        this.p2StatusFilter = p2Status == null || ANY.equals(p2Status) ? null : p2Status;
        applyFilter();
    }

    public SortedSet<String> getMethods() {
        SortedSet<String> methods = new TreeSet<>();
        for (ScanResult r : allResults) methods.add(r.getGroup().getRepresentative().method());
        return methods;
    }

    public ScanResult findByFingerprint(String fingerprint) {
        for (ScanResult r : allResults) {
            if (r.getGroup().getFingerprint().equals(fingerprint)) return r;
        }
        return null;
    }

    /** Re-evaluates filters and repaints, e.g. after a group gained merged duplicates. */
    public void refresh() {
        applyFilter();
    }

    private void applyFilter() {
        filteredResults.clear();
        for (ScanResult r : allResults) {
            if (!matchesSearch(r)) continue;
            if (!matchesVerdictFilter(r)) continue;
            if (methodFilter != null && !methodFilter.equals(r.getGroup().getRepresentative().method())) continue;
            if (p1Id != null && !matchesStatus(r.getStatus(p1Id), p1StatusFilter)) continue;
            if (p2Id != null && !matchesStatus(r.getStatus(p2Id), p2StatusFilter)) continue;
            filteredResults.add(r);
        }
        fireTableDataChanged();
    }

    private static boolean matchesStatus(int code, String statusClass) {
        if (statusClass == null) return true;
        if (NO_RESPONSE.equals(statusClass)) return code < 0;
        return code >= 0 && code / 100 == statusClass.charAt(0) - '0';
    }

    private boolean matchesSearch(ScanResult r) {
        if (searchQuery.isEmpty()) return true;
        String rawPath = r.getGroup().getRepresentative().path();
        String url = rawPath.toLowerCase();
        String decoded = HttpText.displayPath(rawPath).toLowerCase();
        String host = "";
        try { host = r.getGroup().getRepresentative().httpService().host().toLowerCase(); } catch (Exception ignored) {}
        return url.contains(searchQuery) || decoded.contains(searchQuery) || host.contains(searchQuery);
    }

    private boolean matchesVerdictFilter(ScanResult r) {
        if (filterVerdict == null) return true;
        if (p1Id == null || p2Id == null) return filterVerdict == Verdict.PENDING;
        Verdict v = r.getVerdict(p1Id, p2Id, threshold);
        return v == filterVerdict;
    }

    public int getTotalCount()      { return allResults.size(); }
    public int getVerdictCount(Verdict v) {
        if (p1Id == null || p2Id == null) return 0;
        int count = 0;
        for (ScanResult r : allResults) {
            if (r.getVerdict(p1Id, p2Id, threshold) == v) count++;
        }
        return count;
    }
}
