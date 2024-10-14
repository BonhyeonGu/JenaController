import time
import requests
from py2neo import Graph, Node, Relationship

# Neo4j에 연결
graph = Graph("bolt://9bon.org:17687", auth=("neo4j", "qkqh106!"))

# 1단계: 초기 데이터 요청
start_time = time.time()
requests.get("http://localhost:8080/TESTdebugUpdate")
end_time = time.time()
print(f"Step 1 - Initial data request duration: {end_time - start_time:.4f} seconds")

# 2단계: Neo4j 데이터베이스 초기화
start_time = time.time()
graph.delete_all()
end_time = time.time()
print(f"Step 2 - Neo4j database initialization duration: {end_time - start_time:.4f} seconds")

# 3단계: 룸 데이터 가져오기
start_time = time.time()
response = requests.get("http://localhost:8080/category/selectRoomQ")
room_data = response.json()
end_time = time.time()
print(f"Step 3 - Fetching room data duration: {end_time - start_time:.4f} seconds")

# 4단계: 각 룸 데이터 처리
start_time = time.time()
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

    # 'a'와 'b' 노드 사이에 관계 생성
    rel1 = Relationship(a_node, "CONNECTED_TO", b_node)
    rel2 = Relationship(b_node, "CONNECTED_TO", a_node)
    graph.create(rel1)
    graph.create(rel2)
end_time = time.time()
print(f"Step 4 - Processing room data duration: {end_time - start_time:.4f} seconds")

# 5단계: 'b' 노드에서 연결된 'a' 노드들의 qualityIndex 평균 계산 및 차이 업데이트
start_time = time.time()
b_nodes = graph.nodes.match("Building", flag=0)
for b_node in b_nodes:
    connected_a_nodes = list(graph.match((b_node,), r_type="CONNECTED_TO"))
    if connected_a_nodes:
        quality_indexes = [rel.end_node['qualityIndex'] for rel in connected_a_nodes if 'qualityIndex' in rel.end_node]
        if quality_indexes:
            avg_quality_index = sum(quality_indexes) / len(quality_indexes)
            b_node['qualityIndex'] = avg_quality_index
            graph.push(b_node)

            # 각 연결된 'a' 노드와의 차이 업데이트
            for rel in connected_a_nodes:
                quality_difference = rel.end_node['qualityIndex'] - avg_quality_index
                rel['quality_difference'] = quality_difference
                graph.push(rel)

                # 역방향 관계 업데이트
                reverse_rel = Relationship(rel.end_node, "CONNECTED_TO", b_node, quality_difference=-quality_difference)
                graph.create(reverse_rel)
end_time = time.time()
print(f"Step 5 - Updating building nodes with quality index duration: {end_time - start_time:.4f} seconds")

# 6단계: 'a_node' 간의 qualityIndex 차이를 계산하여 관계 생성
start_time = time.time()
a_nodes = list(graph.nodes.match("Room"))
for i in range(len(a_nodes)):
    for j in range(i + 1, len(a_nodes)):
        node1 = a_nodes[i]
        node2 = a_nodes[j]

        # node1에서 node2로의 관계에 대한 qualityIndex 차이 계산
        if 'qualityIndex' in node1 and 'qualityIndex' in node2:
            quality_diff = node1['qualityIndex'] - node2['qualityIndex']
            rel = Relationship(node1, "QUALITY_DIFFERENCE", node2, quality_difference=quality_diff)
            graph.create(rel)
            
            # 양방향 관계를 원한다면, 반대 방향의 관계도 생성
            reverse_quality_diff = node2['qualityIndex'] - node1['qualityIndex']
            reverse_rel = Relationship(node2, "QUALITY_DIFFERENCE", node1, quality_difference=reverse_quality_diff)
            graph.create(reverse_rel)
end_time = time.time()
print(f"Step 6 - Calculating quality index differences between room nodes duration: {end_time - start_time:.4f} seconds")
