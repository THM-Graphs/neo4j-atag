# neo4j-chains
Neo4j stored procedures to build up chains of nodes e.g. for character sequences

:auto MATCH (r:Text)
WHERE r.Value is not null AND NOT((r)-[:NEXT_TOKEN]->())
CALL {
WITH r
CALL thm.fullChain(r, "Value")
} IN TRANSACTIONS OF 1 ROWS
RETURN count(*)

