from rdflib import Graph, Namespace, URIRef
from rdflib.namespace import RDF

# RDF 파일 로드
rdf_file = '0814city.rdf'
g = Graph()
g.parse(rdf_file, format='xml')

# 네임스페이스 정의
STA = Namespace("http://paper.9bon.org/ontologies/sensorthings/1.1#")
BLDG = Namespace("https://dataset-dl.liris.cnrs.fr/rdf-owl-urban-data-ontologies/Ontologies/CityGML/2.0/building#")

# 모든 bldg:Room을 리스트로 가져오기
rooms = list(g.subjects(RDF.type, BLDG.Room))

# 방이 없을 경우를 대비한 예외 처리
if len(rooms) == 0:
    print("No rooms found in the dataset.")
    exit()

# 순차적으로 선택될 방의 인덱스 초기화
room_index = 0

# 모든 bldg:WallSurface에서 hasThing 찾기
for wall_surface in g.subjects(RDF.type, BLDG.WallSurface):
    # 각 벽에서의 sta:hasThing를 찾아서 순차적으로 방에 할당
    for has_thing in g.objects(wall_surface, STA.hasThing):
        # bldg:WallSurface에서 sta:hasThing 제거
        g.remove((wall_surface, STA.hasThing, has_thing))

        # 순차적으로 방을 선택하여 sta:hasThing 추가
        target_room = rooms[room_index]  # 현재 인덱스에 해당하는 방을 선택
        g.add((target_room, STA.hasThing, has_thing))

        # 다음 방으로 이동 (순환 처리)
        room_index = (room_index + 1) % len(rooms)  # 방의 개수에 맞게 인덱스를 순환

# 수정된 RDF 파일을 저장
output_file = 'modified_0814city_sequential_fixed.rdf'
g.serialize(destination=output_file, format='xml')

print(f"Modified RDF saved to {output_file}")
