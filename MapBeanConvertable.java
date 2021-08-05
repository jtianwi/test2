package com.jci.xlauncher.forflutter;

import java.util.HashMap;

/*
 * (C) Copyright 2019 Johnson Controls, Inc.
 * Use or copying of all or any part of the document, except as
 * permitted by the License Agreement is prohibited.
 */
public interface MapBeanConvertable {
    public HashMap<String, Object> toMap();
    public boolean loadFromMap(HashMap<String, Object> map);
}
