package me.proo0xy.data;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DataEntry {
    private final String key;
    private volatile Object value;
}

