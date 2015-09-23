UMLET_JAR=~/Umlet/umlet.jar

sourcesToImages=$(addsuffix .png, $(basename $(1)))

DIAGRAM_SOURCES=$(shell find -L -name "*.uxf" \( -path "./*" \) -print)
DIAGRAM_IMAGES=$(call sourcesToImages, $(DIAGRAM_SOURCES))

.PHONY: diagrams clean

diagrams: $(DIAGRAM_IMAGES)

clean:
	rm -f $(DIAGRAM_IMAGES)

%.png: %.uxf
	mkdir -p $(dir $@)
	java -jar $(UMLET_JAR) -action=convert -format=png -filename='$<' -output='$(basename $@)'
