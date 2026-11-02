package com.idorbola.checker.ui;

import com.idorbola.checker.model.ScanResult;
import com.idorbola.checker.model.Verdict;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

public class ResultTableModel extends AbstractTableModel {

    private static final String[] BASE_COLS = {"#", "Method", "URL", "Group", "P1 Status", "P2 Status", "Verdict", "Similarity"};

    private final List<ScanResult> allResults = new ArrayList<>();
    private final List<ScanResult> filteredResults = new ArrayList<>();

    private String p1Id = null;
    private String p2Id = null;
    private Verdict filterVerdict = null;
    private String searchQuery = "";

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
            case 2 -> r.getGroup().getRepresentative().path();
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

    private void applyFilter() {
        filteredResults.clear();
        for (ScanResult r : allResults) {
            if (!matchesSearch(r)) continue;
            if (!matchesVerdictFilter(r)) continue;
            filteredResults.add(r);
        }
        fireTableDataChanged();
    }

    private boolean matchesSearch(ScanResult r) {
        if (searchQuery.isEmpty()) return true;
        String url = r.getGroup().getRepresentative().path().toLowerCase();
        String host = "";
        try { host = r.getGroup().getRepresentative().httpService().host().toLowerCase(); } catch (Exception ignored) {}
        return url.contains(searchQuery) || host.contains(searchQuery);
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
