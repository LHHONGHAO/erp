-- Backfill legacy global specification values to the category where existing SKUs use them.
-- Safe to run after sku_category_spec_value_upgrade.sql on MySQL 8.0+.
-- Rows used in more than one category are intentionally left unchanged for manual review.
UPDATE jsh_material_attr_value v
JOIN (
  SELECT av.id, MIN(m.category_id) AS category_id
  FROM jsh_material_attr_value av
  JOIN jsh_material_attribute a ON a.id = av.attribute_id
  JOIN jsh_material_extend e
    ON e.delete_flag <> '1'
   AND e.spec_values IS NOT NULL
   AND e.spec_values <> ''
  JOIN jsh_material m
    ON m.id = e.material_id
   AND m.delete_flag <> '1'
   AND m.category_id IS NOT NULL
  JOIN JSON_TABLE(
    e.spec_values,
    '$[*]' COLUMNS (
      attribute_name VARCHAR(255) PATH '$.attributeName',
      value_text VARCHAR(255) PATH '$.valueText'
    )
  ) sv
    ON sv.attribute_name = a.attribute_name
   AND sv.value_text = av.value_text
  WHERE av.category_id IS NULL
    AND av.delete_flag <> '1'
  GROUP BY av.id
  HAVING COUNT(DISTINCT m.category_id) = 1
) mapped ON mapped.id = v.id
SET v.category_id = mapped.category_id
WHERE v.category_id IS NULL;
