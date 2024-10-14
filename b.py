import requests
from py2neo import Graph, Node, Relationship, Subgraph

# Neo4j에 연결
graph = Graph("bolt://9bon.org:17687", auth=("neo4j", "qkqh106!"))

#requests.get("http://localhost:8080/TESTdebugUpdate")

# Neo4j 데이터베이스 초기화
graph.delete_all()

# 룸 데이터 가져오기
response = requests.get("http://localhost:8080/category/selectRoomQ")
room_data = response.json()

# 각 룸 데이터 처리
for key, value in room_data.items():
    quality_index = value.get("qualityIndex")
    # 'NaN' 문자열을 올바르게 처리
    if isinstance(quality_index, str) and quality_index.strip().upper() == "NAN":
        continue

    # 실수로 변환 가능한지 확인
    try:
        quality_index = float(quality_index)
    except ValueError:
        continue

    # 'a' 노드(Room) 생성
    a_node = Node("Room", name=key, roomLabel=value["roomLabel"], qualityIndex=quality_index, flag=1)
    graph.create(a_node)

    # 'b' 노드(Building) 확인 및 생성
    building_part_uri = value["buildingPartUri"]
    b_node = graph.nodes.match("Building", name=building_part_uri).first()
    if not b_node:
        b_node = Node("Building", name=building_part_uri, buildingPartLabel=value["buildingPartLabel"], flag=0)
        graph.create(b_node)

    # 'a'와 'b' 노드 사이에 양방향 관계 생성
    rel1 = Relationship(a_node, "CONNECTED_TO", b_node)
    rel2 = Relationship(b_node, "CONNECTED_TO", a_node)
    graph.create(rel1)
    graph.create(rel2)

# 'b' 노드에서 연결된 'a' 노드들의 qualityIndex 평균 계산
b_nodes = graph.nodes.match("Building", flag=0)
for b_node in b_nodes:
    connected_a_nodes = list(graph.match((b_node,), r_type="CONNECTED_TO"))
    if connected_a_nodes:
        quality_indexes = [node.end_node['qualityIndex'] for node in connected_a_nodes if 'qualityIndex' in node.end_node]
        if quality_indexes:
            avg_quality_index = sum(quality_indexes) / len(quality_indexes)
            b_node['qualityIndex'] = avg_quality_index
            graph.push(b_node)

# 각 노드 관계의 qualityIndex 차이를 속성으로 추가
for rel in graph.relationships.match(r_type="CONNECTED_TO"):
    if 'qualityIndex' in rel.start_node and 'qualityIndex' in rel.end_node:
        quality_diff = abs(rel.start_node['qualityIndex'] - rel.end_node['qualityIndex'])
        rel['quality_difference'] = quality_diff
        graph.push(rel)

# 모든 'a_node'를 가져오고 각 쌍에 대해 관계 생성
a_nodes = list(graph.nodes.match("Room"))
for i in range(len(a_nodes)):
    for j in range(i + 1, len(a_nodes)):
        node1 = a_nodes[i]
        node2 = a_nodes[j]

        # qualityIndex 차이 계산 (노드1에서 노드2로)
        if 'qualityIndex' in node1 and 'qualityIndex' in node2:
            quality_diff = node1['qualityIndex'] - node2['qualityIndex']

            # node1에서 node2로의 관계 생성
            rel = Relationship(node1, "QUALITY_DIFFERENCE", node2, quality_difference=quality_diff)
            graph.create(rel)
            
            # 반대 방향 (노드2에서 노드1로)도 원할 경우
            reverse_diff = node2['qualityIndex'] - node1['qualityIndex']
            reverse_rel = Relationship(node2, "QUALITY_DIFFERENCE", node1, quality_difference=reverse_diff)
            graph.create(reverse_rel)