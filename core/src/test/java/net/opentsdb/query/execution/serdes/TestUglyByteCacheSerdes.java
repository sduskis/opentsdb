// This file is part of OpenTSDB.
// Copyright (C) 2017  The OpenTSDB Authors.
//
// This program is free software: you can redistribute it and/or modify it
// under the terms of the GNU Lesser General Public License as published by
// the Free Software Foundation, either version 2.1 of the License, or (at your
// option) any later version.  This program is distributed in the hope that it
// will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty
// of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser
// General Public License for more details.  You should have received a copy
// of the GNU Lesser General Public License along with this program.  If not,
// see <http://www.gnu.org/licenses/>.
package net.opentsdb.query.execution.serdes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Lists;

import net.opentsdb.data.MillisecondTimeStamp;
import net.opentsdb.data.SimpleStringGroupId;
import net.opentsdb.data.SimpleStringTimeSeriesId;
import net.opentsdb.data.TimeSeriesGroupId;
import net.opentsdb.data.TimeSeriesId;
import net.opentsdb.data.TimeSeriesValue;
import net.opentsdb.data.TimeStamp;
import net.opentsdb.data.iterators.DefaultIteratorGroups;
import net.opentsdb.data.iterators.IteratorGroup;
import net.opentsdb.data.iterators.IteratorGroups;
import net.opentsdb.data.iterators.IteratorStatus;
import net.opentsdb.data.iterators.TimeSeriesIterator;
import net.opentsdb.data.types.numeric.NumericMillisecondShard;
import net.opentsdb.data.types.numeric.NumericType;

public class TestUglyByteCacheSerdes {

  private TimeStamp start;
  private TimeStamp end;
  
  @Before
  public void before() throws Exception {
    start = new MillisecondTimeStamp(1486045800000L);
    end = new MillisecondTimeStamp(1486046000000L);
  }
  
  @SuppressWarnings("unchecked")
  @Test
  public void fullSerdes() throws Exception {
    final IteratorGroups results = new DefaultIteratorGroups();
    
    final TimeSeriesGroupId group_id_a = new SimpleStringGroupId("a");
    final TimeSeriesId id_a = SimpleStringTimeSeriesId.newBuilder()
        .setMetrics(Lists.newArrayList("sys.cpu.user"))
        .addTags("host", "web01")
        .addTags("dc", "phx")
    .build();
    
    NumericMillisecondShard shard = 
        new NumericMillisecondShard(id_a, start, end);
    shard.add(1486045801000L, 42, 1);
    shard.add(1486045871000L, 9866.854, 0);
    shard.add(1486045881000L, -128, 1024);
    results.addIterator(group_id_a, shard);
    
    final TimeSeriesId id_b = SimpleStringTimeSeriesId.newBuilder()
        .setMetrics(Lists.newArrayList("sys.cpu.user"))
        .addTags("host", "web02")
        .addTags("dc", "phx")
    .build();
    shard = new NumericMillisecondShard(id_b, start, end);
    shard.add(1486045801000L, 8, 1);
    shard.add(1486045871000L, Double.NaN, 0);
    shard.add(1486045881000L, 5000, 1024);
    results.addIterator(group_id_a, shard);
    
    final TimeSeriesGroupId group_id_b = new SimpleStringGroupId("b");
    shard = new NumericMillisecondShard(id_a, start, end);
    shard.add(1486045801000L, 5, 1);
    shard.add(1486045871000L, Double.NaN, 0);
    shard.add(1486045881000L, 2, 1024);
    results.addIterator(group_id_b, shard);
    
    shard = new NumericMillisecondShard(id_b, start, end);
    shard.add(1486045801000L, 20, 1);
    shard.add(1486045871000L, Double.NaN, 0);
    shard.add(1486045881000L, 13, 1024);
    results.addIterator(group_id_b, shard);

    final UglyByteIteratorGroupsSerdes serdes = 
        new UglyByteIteratorGroupsSerdes();
    final ByteArrayOutputStream output = new ByteArrayOutputStream();
    serdes.serialize(null, output, results);
    
    output.close();
    byte[] data = output.toByteArray();
    
    final ByteArrayInputStream input = new ByteArrayInputStream(data);
    final IteratorGroups groups = serdes.deserialize(input);
    
    assertEquals(2, groups.groups().size());
    
    // Group A
    IteratorGroup group = groups.group(group_id_a);
    assertEquals(2, group.iterators().size());
    
    // Iterator 1
    TimeSeriesIterator<NumericType> iterator = (TimeSeriesIterator<NumericType>) 
        group.iterators().get(0).iterators().get(0);
    assertEquals(id_a, iterator.id());
    
    assertEquals(IteratorStatus.HAS_DATA, iterator.status());
    TimeSeriesValue<NumericType> v = 
        (TimeSeriesValue<NumericType>) iterator.next();
    assertEquals(1486045801000L, v.timestamp().msEpoch());
    assertTrue(v.value().isInteger());
    assertEquals(42, v.value().longValue());
    assertEquals(1, v.realCount());
    
    assertEquals(IteratorStatus.HAS_DATA, iterator.status());
    v = (TimeSeriesValue<NumericType>) iterator.next();
    assertEquals(1486045871000L, v.timestamp().msEpoch());
    assertFalse(v.value().isInteger());
    assertEquals(9866.854, v.value().doubleValue(), 0.0001);
    assertEquals(0, v.realCount());
    
    assertEquals(IteratorStatus.HAS_DATA, iterator.status());
    v = (TimeSeriesValue<NumericType>) iterator.next();
    assertEquals(1486045881000L, v.timestamp().msEpoch());
    assertTrue(v.value().isInteger());
    assertEquals(-128, v.value().longValue());
    assertEquals(1024, v.realCount());
    
    assertEquals(IteratorStatus.END_OF_DATA, iterator.status());
    
    // Iterator 2
    iterator = (TimeSeriesIterator<NumericType>) 
        group.iterators().get(1).iterators().get(0);
    assertEquals(id_b, iterator.id());
    
    assertEquals(IteratorStatus.HAS_DATA, iterator.status());
    v = (TimeSeriesValue<NumericType>) iterator.next();
    assertEquals(1486045801000L, v.timestamp().msEpoch());
    assertTrue(v.value().isInteger());
    assertEquals(8, v.value().longValue());
    assertEquals(1, v.realCount());
    
    assertEquals(IteratorStatus.HAS_DATA, iterator.status());
    v = (TimeSeriesValue<NumericType>) iterator.next();
    assertEquals(1486045871000L, v.timestamp().msEpoch());
    assertFalse(v.value().isInteger());
    assertTrue(Double.isNaN(v.value().doubleValue()));
    assertEquals(0, v.realCount());
    
    assertEquals(IteratorStatus.HAS_DATA, iterator.status());
    v = (TimeSeriesValue<NumericType>) iterator.next();
    assertEquals(1486045881000L, v.timestamp().msEpoch());
    assertTrue(v.value().isInteger());
    assertEquals(5000, v.value().longValue());
    assertEquals(1024, v.realCount());
    
    assertEquals(IteratorStatus.END_OF_DATA, iterator.status());
    
    // Group B
    group = groups.group(group_id_b);
    assertEquals(2, group.iterators().size());
    
    // Iterator 3
    iterator = (TimeSeriesIterator<NumericType>) 
        group.iterators().get(0).iterators().get(0);
    assertEquals(id_a, iterator.id());
    
    assertEquals(IteratorStatus.HAS_DATA, iterator.status());
    v = (TimeSeriesValue<NumericType>) iterator.next();
    assertEquals(1486045801000L, v.timestamp().msEpoch());
    assertTrue(v.value().isInteger());
    assertEquals(5, v.value().longValue());
    assertEquals(1, v.realCount());
    
    assertEquals(IteratorStatus.HAS_DATA, iterator.status());
    v = (TimeSeriesValue<NumericType>) iterator.next();
    assertEquals(1486045871000L, v.timestamp().msEpoch());
    assertFalse(v.value().isInteger());
    assertTrue(Double.isNaN(v.value().doubleValue()));
    assertEquals(0, v.realCount());
    
    assertEquals(IteratorStatus.HAS_DATA, iterator.status());
    v = (TimeSeriesValue<NumericType>) iterator.next();
    assertEquals(1486045881000L, v.timestamp().msEpoch());
    assertTrue(v.value().isInteger());
    assertEquals(2, v.value().longValue());
    assertEquals(1024, v.realCount());
    
    assertEquals(IteratorStatus.END_OF_DATA, iterator.status());
    
    // Iterator 4
    iterator = (TimeSeriesIterator<NumericType>) 
        group.iterators().get(1).iterators().get(0);
    assertEquals(id_b, iterator.id());
    
    assertEquals(IteratorStatus.HAS_DATA, iterator.status());
    v = (TimeSeriesValue<NumericType>) iterator.next();
    assertEquals(1486045801000L, v.timestamp().msEpoch());
    assertTrue(v.value().isInteger());
    assertEquals(20, v.value().longValue());
    assertEquals(1, v.realCount());
    
    assertEquals(IteratorStatus.HAS_DATA, iterator.status());
    v = (TimeSeriesValue<NumericType>) iterator.next();
    assertEquals(1486045871000L, v.timestamp().msEpoch());
    assertFalse(v.value().isInteger());
    assertTrue(Double.isNaN(v.value().doubleValue()));
    assertEquals(0, v.realCount());
    
    assertEquals(IteratorStatus.HAS_DATA, iterator.status());
    v = (TimeSeriesValue<NumericType>) iterator.next();
    assertEquals(1486045881000L, v.timestamp().msEpoch());
    assertTrue(v.value().isInteger());
    assertEquals(13, v.value().longValue());
    assertEquals(1024, v.realCount());
    
    assertEquals(IteratorStatus.END_OF_DATA, iterator.status());
  }
  
  @Test
  public void empty() throws Exception {
    final IteratorGroups results = new DefaultIteratorGroups();
    final UglyByteIteratorGroupsSerdes serdes = 
        new UglyByteIteratorGroupsSerdes();
    final ByteArrayOutputStream output = new ByteArrayOutputStream();
    serdes.serialize(null, output, results);
    
    output.close();
    byte[] data = output.toByteArray();
    
    final ByteArrayInputStream input = new ByteArrayInputStream(data);
    final IteratorGroups groups = serdes.deserialize(input);
    
    assertTrue(groups.groups().isEmpty());
  }
  
  @Test
  public void exceptions() throws Exception {
    final IteratorGroups results = new DefaultIteratorGroups();
    final UglyByteIteratorGroupsSerdes serdes = 
        new UglyByteIteratorGroupsSerdes();
    final ByteArrayOutputStream output = new ByteArrayOutputStream();
    
    try {
      serdes.serialize(null, null, results);
      fail("Expected IllegalArgumentException");
    } catch (IllegalArgumentException e) { }
    
    try {
      serdes.serialize(null, output, null);
      fail("Expected IllegalArgumentException");
    } catch (IllegalArgumentException e) { }
    
    try {
      serdes.deserialize(null);
      fail("Expected IllegalArgumentException");
    } catch (IllegalArgumentException e) { }
  }
}
